package ru.ssau.rbs.repository.specification;

import org.springframework.data.jpa.domain.Specification;
import ru.ssau.rbs.domain.entity.Resource;
import ru.ssau.rbs.domain.enums.ResourceStatus;
import ru.ssau.rbs.domain.enums.ResourceType;

import java.util.Locale;

public final class ResourceSpecifications {

    private ResourceSpecifications() {
    }

    public static Specification<Resource> hasStatus(ResourceStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Resource> hasType(ResourceType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Resource> minCapacity(int minCapacity) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity);
    }

    public static Specification<Resource> locationContains(String location) {
        String pattern = "%" + location.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("location")), pattern);
    }
}
