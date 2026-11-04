package ru.ssau.rbs.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.ssau.rbs.domain.entity.Resource;
import ru.ssau.rbs.domain.enums.ResourceStatus;
import ru.ssau.rbs.domain.enums.ResourceType;
import ru.ssau.rbs.repository.specification.ResourceSpecifications;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {

    Optional<Resource> findByCode(String code);

    Optional<Resource> findByCalendarCode(String calendarCode);

    boolean existsByCode(String code);

    boolean existsByCalendarCode(String calendarCode);

    List<Resource> findAllByStatusOrderByNameAsc(ResourceStatus status);

    List<Resource> findAllByTypeAndStatusOrderByNameAsc(ResourceType type, ResourceStatus status);

    List<Resource> findAllByLocationIgnoreCase(String location);

    default List<Resource> search(ResourceStatus status,
                                  ResourceType type,
                                  int minCapacity,
                                  String location) {
        Specification<Resource> spec = ResourceSpecifications.hasStatus(status)
                .and(ResourceSpecifications.minCapacity(minCapacity));
        if (type != null) {
            spec = spec.and(ResourceSpecifications.hasType(type));
        }
        if (location != null && !location.isBlank()) {
            spec = spec.and(ResourceSpecifications.locationContains(location));
        }
        return findAll(spec, Sort.by(Sort.Direction.ASC, "name"));
    }

    @Query(value = """
            select * from resources r
            where r.status = :status
              and r.features @> cast(:features as text[])
            order by r.name
            """, nativeQuery = true)
    List<Resource> findAllByFeatures(@Param("status") String status, @Param("features") String[] features);
}
