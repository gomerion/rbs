package ru.ssau.rbs.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.ssau.rbs.domain.entity.SyncOutbox;
import ru.ssau.rbs.domain.enums.OutboxOperation;
import ru.ssau.rbs.domain.enums.OutboxStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SyncOutboxRepository extends JpaRepository<SyncOutbox, Long> {

    List<SyncOutbox> findAllByBookingId(Long bookingId);

    Optional<SyncOutbox> findFirstByBookingIdAndOperationAndStatusNotOrderByCreatedAtDesc(
            Long bookingId, OutboxOperation operation, OutboxStatus status);

    boolean existsByBookingIdAndOperationAndStatusNot(Long bookingId,
                                                      OutboxOperation operation,
                                                      OutboxStatus status);

    default boolean existsActiveByBookingAndOperation(Long bookingId, OutboxOperation operation) {
        return existsByBookingIdAndOperationAndStatusNot(bookingId, operation, OutboxStatus.SENT);
    }

    @Query("""
            select o from SyncOutbox o
            where o.status = :status and o.nextAttemptAt <= :now
            order by o.nextAttemptAt
            """)
    List<SyncOutbox> findReadyToSend(@Param("status") OutboxStatus status,
                                     @Param("now") Instant now,
                                     Pageable pageable);

    List<SyncOutbox> findAllByStatusOrderByNextAttemptAtAsc(OutboxStatus status);

    long countByStatus(OutboxStatus status);
}
