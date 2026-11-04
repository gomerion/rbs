package ru.ssau.rbs.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ssau.rbs.domain.entity.AuditLog;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findAllByObjectTypeAndObjectIdOrderByCreatedAtDesc(String objectType, String objectId);

    List<AuditLog> findAllByActorIdOrderByCreatedAtDesc(Long actorId);

    Page<AuditLog> findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Instant from, Instant to, Pageable pageable);

    long countByAction(String action);
}
