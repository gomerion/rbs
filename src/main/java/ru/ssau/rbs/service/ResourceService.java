package ru.ssau.rbs.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.domain.entity.Resource;
import ru.ssau.rbs.domain.entity.ResourceWorkingHour;
import ru.ssau.rbs.domain.entity.ResourceWorkingHourId;
import ru.ssau.rbs.domain.enums.ResourceStatus;
import ru.ssau.rbs.domain.enums.ResourceType;
import ru.ssau.rbs.dto.common.PageResponse;
import ru.ssau.rbs.dto.resource.AvailabilityResponse;
import ru.ssau.rbs.dto.resource.AvailabilitySlot;
import ru.ssau.rbs.dto.resource.ResourceCreateRequest;
import ru.ssau.rbs.dto.resource.ResourcePatchRequest;
import ru.ssau.rbs.dto.resource.ResourceResponse;
import ru.ssau.rbs.dto.resource.WorkingHoursDto;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.repository.ResourceRepository;
import ru.ssau.rbs.repository.ResourceWorkingHourRepository;
import ru.ssau.rbs.repository.specification.ResourceSpecifications;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private final ResourceRepository resourceRepository;
    private final ResourceWorkingHourRepository workingHourRepository;
    private final AvailabilityCalculator availabilityCalculator;

    @Transactional(readOnly = true)
    public PageResponse<ResourceResponse> search(String type,
                                                 Integer minCapacity,
                                                 List<String> features,
                                                 String location,
                                                 String status,
                                                 String sort,
                                                 Integer limit,
                                                 Integer offset) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        int skip = offset == null ? 0 : offset;
        if (size < 1 || size > MAX_LIMIT) {
            throw ApiException.validationError("limit: допустимое значение от 1 до " + MAX_LIMIT);
        }
        if (skip < 0) {
            throw ApiException.validationError("offset: не может быть отрицательным");
        }
        if (sort != null && !sort.isBlank() && !"name".equalsIgnoreCase(sort.trim())) {
            throw ApiException.validationError("sort: допустимо только name");
        }

        Specification<Resource> spec = ResourceSpecifications.hasStatus(parseStatusOrDefault(status));
        ResourceType resourceType = parseType(type);
        if (resourceType != null) {
            spec = spec.and(ResourceSpecifications.hasType(resourceType));
        }
        if (minCapacity != null) {
            spec = spec.and(ResourceSpecifications.minCapacity(minCapacity));
        }
        if (location != null && !location.isBlank()) {
            spec = spec.and(ResourceSpecifications.locationContains(location));
        }
        List<String> normalizedFeatures = normalizeFeatures(features);
        if (!normalizedFeatures.isEmpty()) {
            spec = spec.and(ResourceSpecifications.idIn(
                    resourceRepository.findIdsByAllFeatures(normalizedFeatures.toArray(String[]::new))));
        }

        long fetch = (long) skip + size;
        if (fetch > Integer.MAX_VALUE) {
            throw ApiException.validationError("offset: слишком большое значение");
        }
        Page<Resource> page = resourceRepository.findAll(spec, PageRequest.of(0, (int) fetch, DEFAULT_SORT));
        List<Resource> content = page.getContent();
        List<Resource> slice = content.size() <= skip ? List.of() : content.subList(skip, content.size());
        Map<Long, List<ResourceWorkingHour>> hours = workingHoursByResource(slice);
        List<ResourceResponse> items = slice.stream()
                .map(resource -> toResponse(resource, hours.getOrDefault(resource.getId(), List.of())))
                .toList();
        return PageResponse.of(items, page.getTotalElements(), size, skip);
    }

    @Transactional(readOnly = true)
    public ResourceResponse get(Long id) {
        Resource resource = findResource(id);
        return toResponse(resource, workingHourRepository.findAllByResourceId(id));
    }

    @Transactional
    public ResourceResponse create(ResourceCreateRequest request) {
        String code = requireText(request.getCode(), "code");
        if (resourceRepository.existsByCode(code)) {
            throw ApiException.conflict("Ресурс с кодом " + code + " уже существует");
        }
        String calendarCode = textOrNull(request.getCalendarCode());
        if (calendarCode != null && resourceRepository.existsByCalendarCode(calendarCode)) {
            throw ApiException.conflict("Ресурс с calendar_code " + calendarCode + " уже существует");
        }
        Resource resource = new Resource();
        resource.setCode(code);
        resource.setCalendarCode(calendarCode);
        resource.setName(requireText(request.getName(), "name"));
        resource.setType(requireType(request.getType()));
        resource.setCapacity(request.getCapacity() == null ? 0 : request.getCapacity());
        resource.setLocation(requireText(request.getLocation(), "location"));
        resource.setFeatures(normalizeFeatures(request.getFeatures()).toArray(String[]::new));
        resource.setTimeZone(requireTimeZone(request.getTimeZone()));
        resource.setStatus(ResourceStatus.ACTIVE);

        Resource saved = resourceRepository.saveAndFlush(resource);
        saveWorkingHours(saved, validateWorkingHours(request.getWorkingHours()));
        return toResponse(saved, workingHourRepository.findAllByResourceId(saved.getId()));
    }

    @Transactional
    public ResourceResponse update(Long id, ResourcePatchRequest request) {
        Resource resource = findResource(id);
        if (request.getCode() != null) {
            String code = requireText(request.getCode(), "code");
            if (!code.equals(resource.getCode()) && resourceRepository.existsByCode(code)) {
                throw ApiException.conflict("Ресурс с кодом " + code + " уже существует");
            }
            resource.setCode(code);
        }
        if (request.getName() != null) {
            resource.setName(requireText(request.getName(), "name"));
        }
        if (request.getType() != null) {
            resource.setType(requireType(request.getType()));
        }
        if (request.getCapacity() != null) {
            resource.setCapacity(request.getCapacity());
        }
        if (request.getLocation() != null) {
            resource.setLocation(requireText(request.getLocation(), "location"));
        }
        if (request.getFeatures() != null) {
            resource.setFeatures(normalizeFeatures(request.getFeatures()).toArray(String[]::new));
        }
        if (request.getTimeZone() != null) {
            resource.setTimeZone(requireTimeZone(request.getTimeZone()));
        }
        if (request.getCalendarCode() != null) {
            String calendarCode = textOrNull(request.getCalendarCode());
            if (calendarCode != null
                    && !calendarCode.equals(resource.getCalendarCode())
                    && resourceRepository.existsByCalendarCode(calendarCode)) {
                throw ApiException.conflict("Ресурс с calendar_code " + calendarCode + " уже существует");
            }
            resource.setCalendarCode(calendarCode);
        }
        if (request.getStatus() != null) {
            ResourceStatus status = requireStatus(request.getStatus());
            if (status == ResourceStatus.ARCHIVED) {
                resource.archive();
            } else {
                resource.activate();
            }
        }

        Resource saved = resourceRepository.saveAndFlush(resource);
        if (request.getWorkingHours() != null) {
            workingHourRepository.deleteAllByResourceId(id);
            saveWorkingHours(saved, validateWorkingHours(request.getWorkingHours()));
        }
        return toResponse(saved, workingHourRepository.findAllByResourceId(saved.getId()));
    }

    @Transactional
    public ResourceResponse archive(Long id) {
        Resource resource = findResource(id);
        resource.archive();
        Resource saved = resourceRepository.saveAndFlush(resource);
        return toResponse(saved, workingHourRepository.findAllByResourceId(saved.getId()));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(Long id, Instant from, Instant to) {
        if (from == null || to == null) {
            throw ApiException.validationError("from и to обязательны");
        }
        if (!from.isBefore(to)) {
            throw ApiException.validationError("from: должен быть раньше to");
        }
        Resource resource = findResource(id);
        List<AvailabilitySlot> slots = availabilityCalculator.slots(resource, from, to);
        return new AvailabilityResponse(resource.getId(), slots);
    }

    private Resource findResource(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Ресурс не найден: " + id));
    }

    private Map<Long, List<ResourceWorkingHour>> workingHoursByResource(List<Resource> resources) {
        if (resources.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = resources.stream().map(Resource::getId).toList();
        return workingHourRepository.findAllByResourceIds(ids).stream()
                .collect(Collectors.groupingBy(hour -> hour.getId().getResourceId()));
    }

    private void saveWorkingHours(Resource resource, List<WorkingHoursDto> hours) {
        if (hours.isEmpty()) {
            return;
        }
        List<ResourceWorkingHour> rows = new ArrayList<>(hours.size());
        for (WorkingHoursDto dto : hours) {
            ResourceWorkingHour hour = new ResourceWorkingHour();
            hour.setId(new ResourceWorkingHourId(resource.getId(), dto.getWeekday().shortValue(), dto.getStartTime()));
            hour.setResource(resource);
            hour.setEndTime(dto.getEndTime());
            rows.add(hour);
        }
        workingHourRepository.saveAll(rows);
    }

    private static List<WorkingHoursDto> validateWorkingHours(List<WorkingHoursDto> hours) {
        if (hours == null || hours.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        for (WorkingHoursDto hour : hours) {
            if (hour == null || hour.getWeekday() == null || hour.getStartTime() == null || hour.getEndTime() == null) {
                throw ApiException.validationError("working_hours: обязательны weekday, start_time, end_time");
            }
            if (hour.getWeekday() < 1 || hour.getWeekday() > 7) {
                throw ApiException.validationError("working_hours: weekday принимает значения от 1 до 7");
            }
            if (!hour.getStartTime().isBefore(hour.getEndTime())) {
                throw ApiException.validationError("working_hours: start_time должен быть раньше end_time");
            }
            if (!seen.add(hour.getWeekday() + " " + hour.getStartTime())) {
                throw ApiException.validationError("working_hours: повтор интервала для дня " + hour.getWeekday());
            }
        }
        return List.copyOf(hours);
    }

    private static ResourceResponse toResponse(Resource resource, List<ResourceWorkingHour> hours) {
        List<WorkingHoursDto> workingHours = hours.stream()
                .sorted(Comparator.comparing(hour -> hour.getId().getWeekday()))
                .map(hour -> new WorkingHoursDto(hour.getId().getWeekday().intValue(),
                        hour.getId().getStartTime(), hour.getEndTime()))
                .toList();
        String[] features = resource.getFeatures();
        return new ResourceResponse(
                resource.getId(),
                resource.getCode(),
                resource.getName(),
                resource.getType().getCode(),
                resource.getCapacity(),
                resource.getLocation(),
                features == null ? List.of() : List.of(features),
                resource.getTimeZone(),
                resource.getCalendarCode(),
                resource.getStatus().getCode(),
                workingHours);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApiException.validationError(field + ": обязательное поле");
        }
        return value.trim();
    }

    private static String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireTimeZone(String value) {
        String zone = requireText(value, "timezone");
        try {
            return ZoneId.of(zone).getId();
        } catch (DateTimeException ex) {
            throw ApiException.validationError("timezone: неизвестный часовой пояс " + zone);
        }
    }

    private static ResourceType requireType(String value) {
        ResourceType type = parseType(value);
        if (type == null) {
            throw ApiException.validationError("type: обязательное поле");
        }
        return type;
    }

    private static ResourceType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ResourceType.fromCode(value.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validationError("type: неизвестный тип ресурса " + value);
        }
    }

    private static ResourceStatus requireStatus(String value) {
        ResourceStatus status = parseStatus(value);
        if (status == null) {
            throw ApiException.validationError("status: обязательное поле");
        }
        return status;
    }

    private static ResourceStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ResourceStatus.fromCode(value.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validationError("status: неизвестный статус ресурса " + value);
        }
    }

    private static ResourceStatus parseStatusOrDefault(String value) {
        ResourceStatus status = parseStatus(value);
        return status == null ? ResourceStatus.ACTIVE : status;
    }

    private static List<String> normalizeFeatures(List<String> features) {
        if (features == null || features.isEmpty()) {
            return List.of();
        }
        return features.stream()
                .filter(feature -> feature != null && !feature.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
