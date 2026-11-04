package ru.ssau.rbs.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.ssau.rbs.domain.entity.Booking;
import ru.ssau.rbs.domain.enums.BookingStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllBySeriesId(Long seriesId);

    Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    long countByUserIdAndStatusIn(Long userId, Collection<BookingStatus> statuses);

    long countByUserIdAndStatus(Long userId, BookingStatus status);

    long countByResourceIdAndStatusIn(Long resourceId, Collection<BookingStatus> statuses);

    @Query("""
            select b from Booking b
            where b.resource.id = :resourceId
              and b.status in :statuses
              and b.startsAt < :endsAt
              and b.endsAt > :startsAt
            order by b.startsAt
            """)
    List<Booking> findOverlapping(@Param("resourceId") Long resourceId,
                                  @Param("statuses") Collection<BookingStatus> statuses,
                                  @Param("startsAt") Instant startsAt,
                                  @Param("endsAt") Instant endsAt);

    @Query("""
            select count(b) from Booking b
            where b.resource.id = :resourceId
              and b.status in :statuses
              and b.startsAt < :endsAt
              and b.endsAt > :startsAt
            """)
    long countOverlapping(@Param("resourceId") Long resourceId,
                          @Param("statuses") Collection<BookingStatus> statuses,
                          @Param("startsAt") Instant startsAt,
                          @Param("endsAt") Instant endsAt);

    default boolean existsOverlapping(Long resourceId, Instant startsAt, Instant endsAt) {
        return countOverlapping(resourceId, BookingStatus.ACTIVE, startsAt, endsAt) > 0;
    }

    @Query("""
            select b from Booking b
            where b.user.id = :userId
              and b.status in :statuses
              and b.startsAt < :to
              and b.endsAt > :from
            order by b.startsAt
            """)
    List<Booking> findByUserAndPeriod(@Param("userId") Long userId,
                                      @Param("statuses") Collection<BookingStatus> statuses,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);

    @Query("""
            select b from Booking b
            where b.resource.id = :resourceId
              and b.status in :statuses
              and b.startsAt < :to
              and b.endsAt > :from
            order by b.startsAt
            """)
    List<Booking> findByResourceAndPeriod(@Param("resourceId") Long resourceId,
                                          @Param("statuses") Collection<BookingStatus> statuses,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

    @Query("""
            select b from Booking b
            where b.user.department.id = :departmentId
              and b.status in :statuses
              and b.startsAt < :to
              and b.endsAt > :from
            order by b.startsAt
            """)
    List<Booking> findByDepartmentAndPeriod(@Param("departmentId") Long departmentId,
                                            @Param("statuses") Collection<BookingStatus> statuses,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);

    List<Booking> findAllByStatusAndEndsAtBefore(BookingStatus status, Instant endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
}
