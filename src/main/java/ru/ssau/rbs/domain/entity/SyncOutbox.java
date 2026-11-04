package ru.ssau.rbs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.ssau.rbs.domain.enums.OutboxOperation;
import ru.ssau.rbs.domain.enums.OutboxStatus;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "sync_outbox")
@Getter
@Setter
@NoArgsConstructor
public class SyncOutbox extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "operation", nullable = false)
    private OutboxOperation operation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "status", nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Override
    protected void onCreated() {
        super.onCreated();
        if (nextAttemptAt == null) {
            nextAttemptAt = Instant.now();
        }
    }

    public void markInFlight() {
        this.status = OutboxStatus.IN_FLIGHT;
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.lastError = null;
    }

    public void scheduleRetry(String error) {
        this.status = OutboxStatus.PENDING;
        this.lastError = error;
        this.attempts++;
        this.nextAttemptAt = Instant.now().plusSeconds(backoffSeconds(this.attempts));
    }

    public void deadLetter(String error) {
        this.status = OutboxStatus.DEAD_LETTER;
        this.lastError = error;
        this.attempts++;
    }

    private static long backoffSeconds(int attempt) {
        return attempt <= 0 ? 1L : Math.min(16L, 1L << Math.min(attempt - 1, 4));
    }
}
