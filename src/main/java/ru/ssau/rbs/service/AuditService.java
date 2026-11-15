package ru.ssau.rbs.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.domain.entity.AuditLog;
import ru.ssau.rbs.dto.audit.AuditLogResponse;
import ru.ssau.rbs.dto.common.PageResponse;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.repository.AuditLogRepository;
import ru.ssau.rbs.repository.UserRepository;
import ru.ssau.rbs.repository.specification.AuditLogSpecifications;
import ru.ssau.rbs.security.RequestIdFilter;
import ru.ssau.rbs.security.UserPrincipal;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void record(Long actorId, String action, String objectType, Object objectId,
                       Map<String, Object> before, Map<String, Object> after) {
        store(actorId, action, objectType, objectId, before, after);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(Long actorId, String action, String objectType, Object objectId) {
        store(actorId, action, objectType, objectId, null, Map.of("denied", true));
    }

    @Transactional
    public void recordCurrent(String action, String objectType, Object objectId,
                              Map<String, Object> before, Map<String, Object> after) {
        store(currentActorId(), action, objectType, objectId, before, after);
    }

    private static Long currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(Long actorId, String action, String objectType, String objectId,
                                               Instant from, Instant to, Integer limit, Integer offset) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        int skip = offset == null ? 0 : offset;
        if (size < 1 || size > MAX_LIMIT) {
            throw ApiException.validationError("limit: допустимое значение от 1 до " + MAX_LIMIT);
        }
        if (skip < 0) {
            throw ApiException.validationError("offset: не может быть отрицательным");
        }
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (actorId != null) {
            spec = spec.and(AuditLogSpecifications.hasActor(actorId));
        }
        if (action != null && !action.isBlank()) {
            spec = spec.and(AuditLogSpecifications.hasAction(action.trim()));
        }
        if (objectType != null && !objectType.isBlank()) {
            spec = spec.and(AuditLogSpecifications.hasObjectType(objectType.trim()));
        }
        if (objectId != null && !objectId.isBlank()) {
            spec = spec.and(AuditLogSpecifications.hasObjectId(objectId.trim()));
        }
        if (from != null) {
            spec = spec.and(AuditLogSpecifications.createdFrom(from));
        }
        if (to != null) {
            spec = spec.and(AuditLogSpecifications.createdTo(to));
        }
        Page<AuditLog> page = auditLogRepository.findAll(spec, PageRequest.of(skip / size, size, NEWEST_FIRST));
        List<AuditLogResponse> items = page.getContent().stream().map(AuditService::toResponse).toList();
        return PageResponse.of(items, page.getTotalElements(), size, skip);
    }

    @Transactional(readOnly = true)
    public AuditLogResponse get(Long id) {
        return auditLogRepository.findById(id)
                .map(AuditService::toResponse)
                .orElseThrow(() -> ApiException.notFound("Запись аудита не найдена: " + id));
    }

    private void store(Long actorId, String action, String objectType, Object objectId,
                       Map<String, Object> before, Map<String, Object> after) {
        AuditLog entry = new AuditLog();
        entry.setActor(actorId == null ? null : userRepository.getReferenceById(actorId));
        entry.setAction(action);
        entry.setObjectType(objectType);
        entry.setObjectId(String.valueOf(objectId));
        entry.setBeforeState(before);
        entry.setAfterState(after);
        entry.setRequestId(RequestIdFilter.currentRequestId());
        auditLogRepository.save(entry);
    }

    private static AuditLogResponse toResponse(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getActor() == null ? null : entry.getActor().getId(),
                entry.getAction(),
                entry.getObjectType(),
                entry.getObjectId(),
                entry.getBeforeState(),
                entry.getAfterState(),
                entry.getRequestId(),
                entry.getCreatedAt());
    }
}
