package ru.ssau.rbs.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.ssau.rbs.domain.entity.Reminder;
import ru.ssau.rbs.domain.enums.BookingStatus;
import ru.ssau.rbs.domain.enums.ReminderStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    Optional<Reminder> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    void deleteByBookingId(Long bookingId);

    @Query("""
            select r from Reminder r
            where r.status = :status and r.remindAt <= :now
            order by r.remindAt
            """)
    List<Reminder> findDue(@Param("status") ReminderStatus status,
                           @Param("now") Instant now,
                           Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from Reminder r
            where r.status = :status and r.remindAt <= :now
            order by r.remindAt
            """)
    List<Reminder> findDueForUpdate(@Param("status") ReminderStatus status,
                                    @Param("now") Instant now,
                                    Pageable pageable);

    long countByStatus(ReminderStatus status);

    @Query("""
            select r from Reminder r
            where r.status = :reminderStatus
              and r.booking.status in :bookingStatuses
              and r.remindAt <= :now
            order by r.remindAt
            """)
    List<Reminder> findDueForBookingStatuses(@Param("reminderStatus") ReminderStatus reminderStatus,
                                             @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses,
                                             @Param("now") Instant now);
}
