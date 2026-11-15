package ru.ssau.rbs.repository.specification;

import java.time.Instant;
import java.util.Collection;

import org.springframework.data.jpa.domain.Specification;

import ru.ssau.rbs.domain.entity.Booking;
import ru.ssau.rbs.domain.enums.BookingStatus;

public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    public static Specification<Booking> belongsTo(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Booking> belongsToDepartment(Long departmentId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("department").get("id"), departmentId);
    }

    public static Specification<Booking> hasResource(Long resourceId) {
        return (root, query, cb) -> cb.equal(root.get("resource").get("id"), resourceId);
    }

    public static Specification<Booking> hasStatuses(Collection<BookingStatus> statuses) {
        if (statuses.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Booking> startsBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get("startsAt"), to);
    }

    public static Specification<Booking> endsAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThan(root.get("endsAt"), from);
    }
}
