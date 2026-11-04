package ru.ssau.rbs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.ssau.rbs.domain.entity.ExternalBusy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExternalBusyRepository extends JpaRepository<ExternalBusy, Long> {

    Optional<ExternalBusy> findByResourceIdAndExternalId(Long resourceId, String externalId);

    boolean existsByResourceIdAndExternalId(Long resourceId, String externalId);

    List<ExternalBusy> findAllByResourceIdOrderByStartsAtAsc(Long resourceId);

    List<ExternalBusy> findAllByFetchedAtBefore(Instant threshold);

    @Query("""
            select count(e) from ExternalBusy e
            where e.resource.id = :resourceId
              and e.startsAt < :endsAt
              and e.endsAt > :startsAt
            """)
    long countOverlapping(@Param("resourceId") Long resourceId,
                          @Param("startsAt") Instant startsAt,
                          @Param("endsAt") Instant endsAt);

    default boolean existsOverlapping(Long resourceId, Instant startsAt, Instant endsAt) {
        return countOverlapping(resourceId, startsAt, endsAt) > 0;
    }

    void deleteAllByResourceId(Long resourceId);
}
