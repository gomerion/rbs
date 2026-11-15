package ru.ssau.rbs.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.config.BookingProperties;
import ru.ssau.rbs.domain.entity.Booking;
import ru.ssau.rbs.domain.entity.Resource;
import ru.ssau.rbs.domain.entity.ResourceWorkingHour;
import ru.ssau.rbs.domain.entity.User;
import ru.ssau.rbs.domain.enums.BookingStatus;
import ru.ssau.rbs.domain.enums.BookingSyncStatus;
import ru.ssau.rbs.domain.enums.ResourceStatus;
import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.dto.booking.BookingCreateRequest;
import ru.ssau.rbs.dto.booking.BookingMoveRequest;
import ru.ssau.rbs.dto.booking.BookingResponse;
import ru.ssau.rbs.dto.booking.BookingResult;
import ru.ssau.rbs.dto.common.PageResponse;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.repository.BookingRepository;
import ru.ssau.rbs.repository.ExternalBusyRepository;
import ru.ssau.rbs.repository.ResourceRepository;
import ru.ssau.rbs.repository.ResourceWorkingHourRepository;
import ru.ssau.rbs.repository.UserRepository;
import ru.ssau.rbs.repository.specification.BookingSpecifications;
import ru.ssau.rbs.security.UserPrincipal;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("startsAt"), Sort.Order.desc("id"));

    private final BookingRepository bookingRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceWorkingHourRepository workingHourRepository;
    private final ExternalBusyRepository externalBusyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final BookingProperties properties;
    private final TransactionTemplate transactionTemplate;

    public BookingResult create(BookingCreateRequest request, String idempotencyKey, UserPrincipal actor) {
        User target = resolveTarget(request.getUserId(), actor);
        String key = blankToNull(idempotencyKey);
        if (key != null) {
            Optional<Booking> replay = bookingRepository.findByUserIdAndIdempotencyKey(target.getId(), key);
            if (replay.isPresent()) {
                return new BookingResult(toResponse(replay.get()), true);
            }
        }
        Resource resource = findResource(request.getResourceId());
        if (resource.getStatus() == ResourceStatus.ARCHIVED) {
            throw ApiException.conflict("resource_archived", "Ресурс в архиве: " + resource.getCode());
        }
        Instant startsAt = request.getStartsAt();
        Instant endsAt = request.getEndsAt();
        validateInterval(resource, startsAt, endsAt);
        if (externalBusyRepository.existsOverlapping(resource.getId(), startsAt, endsAt)) {
            throw ApiException.conflict("external_busy", "Интервал занят во внешнем календаре");
        }
        String purpose = request.getPurpose().trim();
        Booking saved;
        try {
            saved = transactionTemplate.execute(status -> {
                if (target.getRole() != UserRole.ADMIN) {
                    userRepository.findByIdForUpdate(target.getId())
                            .orElseThrow(() -> ApiException.notFound("Пользователь не найден: " + target.getId()));
                    enforceActiveLimit(target);
                }
                List<Booking> overlapping = bookingRepository.findOverlapping(
                        resource.getId(), BookingStatus.ACTIVE, startsAt, endsAt);
                if (!overlapping.isEmpty()) {
                    throw conflictWithBooking(overlapping.getFirst().getId());
                }
                Booking booking = new Booking();
                booking.setResource(resourceRepository.getReferenceById(resource.getId()));
                booking.setUser(userRepository.getReferenceById(target.getId()));
                booking.setCreatedBy(userRepository.getReferenceById(actor.getId()));
                booking.setStartsAt(startsAt);
                booking.setEndsAt(endsAt);
                booking.setPurpose(purpose);
                booking.setIdempotencyKey(key);
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setSyncStatus(BookingSyncStatus.PENDING);
                Booking result = bookingRepository.saveAndFlush(booking);
                auditService.record(actor.getId(), "booking.create", "booking", result.getId(),
                        null, bookingState(result));
                return result;
            });
        } catch (DataIntegrityViolationException ex) {
            throw conflictWithBooking(resolveConflictingId(resource.getId(), startsAt, endsAt));
        }
        return new BookingResult(toResponse(saved), false);
    }

    public BookingResponse cancel(Long id, UserPrincipal actor) {
        Booking booking = findBooking(id);
        requireVisible(actor, booking, "booking.cancel");
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.conflict("already_cancelled", "Бронь уже отменена");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw ApiException.conflict("conflict", "Завершённую бронь нельзя отменить");
        }
        Instant now = Instant.now();
        if (!now.isBefore(booking.getStartsAt())) {
            throw ApiException.conflict("cancel_window_expired", "Бронь уже началась, отмена невозможна");
        }
        if (actor.getRole() == UserRole.EMPLOYEE) {
            Instant deadline = booking.getStartsAt().minus(Duration.ofHours(properties.getCancelHoursEmployee()));
            if (now.isAfter(deadline)) {
                throw ApiException.conflict("cancel_window_expired",
                        "Отмена возможна не позднее чем за " + properties.getCancelHoursEmployee() + " ч до начала");
            }
        }
        Map<String, Object> before = bookingState(booking);
        Booking saved = transactionTemplate.execute(status -> {
            Booking managed = bookingRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> ApiException.notFound("Бронь не найдена: " + id));
            managed.cancel(now);
            Booking result = bookingRepository.saveAndFlush(managed);
            auditService.record(actor.getId(), "booking.cancel", "booking", id, before, bookingState(result));
            return result;
        });
        return toResponse(saved);
    }

    public BookingResponse move(Long id, BookingMoveRequest request, UserPrincipal actor) {
        Booking booking = findBooking(id);
        requireVisible(actor, booking, "booking.move");
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.conflict("already_cancelled", "Бронь уже отменена");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw ApiException.conflict("conflict", "Завершённую бронь нельзя перенести");
        }
        Resource resource = findResource(booking.getResource().getId());
        if (resource.getStatus() == ResourceStatus.ARCHIVED) {
            throw ApiException.conflict("resource_archived", "Ресурс в архиве: " + resource.getCode());
        }
        Instant startsAt = request.getStartsAt();
        Instant endsAt = request.getEndsAt();
        validateInterval(resource, startsAt, endsAt);
        long oldMinutes = Duration.between(booking.getStartsAt(), booking.getEndsAt()).toMinutes();
        long newMinutes = Duration.between(startsAt, endsAt).toMinutes();
        if (oldMinutes != newMinutes) {
            throw ApiException.validationError("Длительность брони при переносе не меняется");
        }
        if (externalBusyRepository.existsOverlapping(resource.getId(), startsAt, endsAt)) {
            throw ApiException.conflict("external_busy", "Интервал занят во внешнем календаре");
        }
        List<Booking> overlapping = bookingRepository
                .findOverlapping(resource.getId(), BookingStatus.ACTIVE, startsAt, endsAt).stream()
                .filter(other -> !other.getId().equals(id))
                .toList();
        if (!overlapping.isEmpty()) {
            throw conflictWithBooking(overlapping.getFirst().getId());
        }
        Map<String, Object> before = bookingState(booking);
        Booking saved;
        try {
            saved = transactionTemplate.execute(status -> {
                Booking managed = bookingRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> ApiException.notFound("Бронь не найдена: " + id));
                managed.move(startsAt, endsAt);
                Booking result = bookingRepository.saveAndFlush(managed);
                auditService.record(actor.getId(), "booking.move", "booking", id, before, bookingState(result));
                return result;
            });
        } catch (DataIntegrityViolationException ex) {
            throw conflictWithBooking(resolveConflictingId(resource.getId(), startsAt, endsAt));
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long id, UserPrincipal actor) {
        Booking booking = findBooking(id);
        requireVisible(actor, booking, "booking.read");
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> mine(UserPrincipal actor, String status, Instant from, Instant to,
                                              Integer limit, Integer offset) {
        return list(actor.getId(), null, status, from, to, limit, offset);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(Long userId, Long departmentId, String status, Instant from,
                                              Instant to, Integer limit, Integer offset) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        int skip = offset == null ? 0 : offset;
        if (size < 1 || size > MAX_LIMIT) {
            throw ApiException.validationError("limit: допустимое значение от 1 до " + MAX_LIMIT);
        }
        if (skip < 0) {
            throw ApiException.validationError("offset: не может быть отрицательным");
        }
        Specification<Booking> spec = (root, query, cb) -> cb.conjunction();
        if (userId != null) {
            spec = spec.and(BookingSpecifications.belongsTo(userId));
        }
        if (departmentId != null) {
            spec = spec.and(BookingSpecifications.belongsToDepartment(departmentId));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and(BookingSpecifications.hasStatuses(List.of(parseStatus(status))));
        }
        if (from != null) {
            spec = spec.and(BookingSpecifications.endsAfter(from));
        }
        if (to != null) {
            spec = spec.and(BookingSpecifications.startsBefore(to));
        }
        Page<Booking> page = bookingRepository.findAll(spec, PageRequest.of(skip / size, size, LATEST_FIRST));
        List<BookingResponse> items = page.getContent().stream().map(BookingService::toResponse).toList();
        return PageResponse.of(items, page.getTotalElements(), size, skip);
    }

    private void enforceActiveLimit(User target) {
        long limit = switch (target.getRole()) {
            case ADMIN -> Long.MAX_VALUE;
            case MANAGER -> properties.getMaxActiveManager();
            case EMPLOYEE -> properties.getMaxActiveEmployee();
        };
        if (limit == Long.MAX_VALUE) {
            return;
        }
        long active = bookingRepository.countByUserIdAndStatusIn(target.getId(), BookingStatus.ACTIVE);
        if (active >= limit) {
            throw ApiException.conflict("active_limit", "Превышен лимит активных броней: " + limit);
        }
    }

    private void validateInterval(Resource resource, Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null) {
            throw ApiException.validationError("starts_at и ends_at обязательны");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw ApiException.badRequest("duration_out_of_range", "Окончание должно быть позже начала");
        }
        ZoneId zone = zone(resource);
        if (!isAligned(startsAt, zone) || !isAligned(endsAt, zone)) {
            throw ApiException.badRequest("slot_alignment",
                    "Интервал не выровнен по сетке " + properties.getGridMinutes() + " минут");
        }
        long minutes = Duration.between(startsAt, endsAt).toMinutes();
        if (minutes < properties.getMinDurationMinutes() || minutes > properties.getMaxDurationMinutes()) {
            throw ApiException.badRequest("duration_out_of_range", "Длительность должна быть от "
                    + properties.getMinDurationMinutes() + " до " + properties.getMaxDurationMinutes() + " минут");
        }
        Instant now = Instant.now();
        if (startsAt.isBefore(now)) {
            throw ApiException.badRequest("booking_in_past", "Начало брони в прошлом");
        }
        if (startsAt.isAfter(now.plus(Duration.ofDays(properties.getAdvanceDays())))) {
            throw ApiException.badRequest("advance_limit",
                    "Бронирование возможно не дальше " + properties.getAdvanceDays() + " дней");
        }
        if (!insideWorkingHours(resource, startsAt, endsAt, zone)) {
            throw ApiException.badRequest("outside_working_hours", "Интервал выходит за рабочие часы ресурса");
        }
    }

    private boolean insideWorkingHours(Resource resource, Instant startsAt, Instant endsAt, ZoneId zone) {
        ZonedDateTime localStart = startsAt.atZone(zone);
        ZonedDateTime localEnd = endsAt.atZone(zone);
        short weekday = (short) localStart.getDayOfWeek().getValue();
        LocalTime startTime = localStart.toLocalTime();
        LocalTime endTime = localEnd.toLocalTime();
        for (ResourceWorkingHour hour : workingHourRepository.findAllByResourceIdAndWeekday(resource.getId(), weekday)) {
            if (!hour.getId().getStartTime().isAfter(startTime) && !hour.getEndTime().isBefore(endTime)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAligned(Instant instant, ZoneId zone) {
        ZonedDateTime local = instant.atZone(zone);
        return local.getMinute() % properties.getGridMinutes() == 0
                && local.getSecond() == 0
                && local.getNano() == 0;
    }

    private void requireVisible(UserPrincipal actor, Booking booking, String action) {
        Long ownerId = booking.getUser().getId();
        if (ownerId.equals(actor.getId())) {
            return;
        }
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        if (actor.getRole() == UserRole.MANAGER) {
            Long ownerDepartment = bookingRepository.findOwnerDepartmentId(booking.getId()).orElse(null);
            if (ownerDepartment != null && ownerDepartment.equals(actor.getDepartmentId())) {
                return;
            }
        }
        auditService.recordDenied(actor.getId(), action, "booking", booking.getId());
        throw ApiException.notFound("Бронь не найдена: " + booking.getId());
    }

    private User resolveTarget(Long userId, UserPrincipal actor) {
        if (userId == null || userId.equals(actor.getId())) {
            return userRepository.findById(actor.getId())
                    .orElseThrow(() -> ApiException.notFound("Пользователь не найден: " + actor.getId()));
        }
        User target = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден: " + userId));
        if (actor.getRole() == UserRole.ADMIN) {
            return target;
        }
        if (actor.getRole() == UserRole.MANAGER) {
            Long targetDepartment = userRepository.findDepartmentIdById(target.getId()).orElse(null);
            if (actor.getDepartmentId() != null && actor.getDepartmentId().equals(targetDepartment)) {
                return target;
            }
        }
        throw ApiException.forbidden("Роль не позволяет бронировать за другого сотрудника");
    }

    private Long resolveConflictingId(Long resourceId, Instant startsAt, Instant endsAt) {
        try {
            return transactionTemplate.execute(status -> bookingRepository
                    .findOverlapping(resourceId, BookingStatus.ACTIVE, startsAt, endsAt).stream()
                    .map(Booking::getId)
                    .findFirst()
                    .orElse(null));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private ApiException conflictWithBooking(Long conflictingId) {
        ApiException exception = ApiException.conflict("conflict", "Интервал пересекается с подтверждённой бронью");
        if (conflictingId != null) {
            exception.withProperty("conflicting_booking_id", conflictingId);
        }
        return exception;
    }

    private Resource findResource(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Ресурс не найден: " + id));
    }

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Бронь не найдена: " + id));
    }

    private static ZoneId zone(Resource resource) {
        return ZoneId.of(resource.getTimeZone());
    }

    private static BookingStatus parseStatus(String value) {
        try {
            return BookingStatus.fromCode(value.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validationError("status: неизвестный статус брони " + value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> bookingState(Booking booking) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("resource_id", booking.getResource().getId());
        state.put("user_id", booking.getUser().getId());
        state.put("starts_at", booking.getStartsAt().toString());
        state.put("ends_at", booking.getEndsAt().toString());
        state.put("status", booking.getStatus().getCode());
        state.put("purpose", booking.getPurpose());
        return state;
    }

    private static BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getResource().getId(),
                booking.getUser().getId(),
                booking.getCreatedBy().getId(),
                booking.getStartsAt(),
                booking.getEndsAt(),
                booking.getStatus().getCode(),
                booking.getPurpose(),
                booking.getSyncStatus().getCode(),
                booking.getCreatedAt());
    }
}
