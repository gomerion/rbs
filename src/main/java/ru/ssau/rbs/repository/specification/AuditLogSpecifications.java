package ru.ssau.rbs.repository.specification;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import ru.ssau.rbs.domain.entity.AuditLog;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> hasActor(Long actorId) {
        return (root, query, cb) -> cb.equal(root.get("actor").get("id"), actorId);
    }

    public static Specification<AuditLog> hasAction(String action) {
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> hasObjectType(String objectType) {
        return (root, query, cb) -> cb.equal(root.get("objectType"), objectType);
    }

    public static Specification<AuditLog> hasObjectId(String objectId) {
        return (root, query, cb) -> cb.equal(root.get("objectId"), objectId);
    }

    public static Specification<AuditLog> createdFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AuditLog> createdTo(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }
}
