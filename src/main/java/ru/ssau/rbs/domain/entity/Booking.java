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
import ru.ssau.rbs.domain.enums.BookingStatus;
import ru.ssau.rbs.domain.enums.BookingSyncStatus;

import java.time.Instant;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private BookingSeries series;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "status", nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "purpose", nullable = false, columnDefinition = "text")
    private String purpose;

    @Column(name = "idempotency_key", columnDefinition = "text")
    private String idempotencyKey;

    @Column(name = "external_event_id", columnDefinition = "text")
    private String externalEventId;

    @Column(name = "sync_status", nullable = false)
    private BookingSyncStatus syncStatus = BookingSyncStatus.PENDING;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public boolean isActive() {
        return status != null && status.isActive();
    }

    public void cancel(Instant cancelledAt) {
        if (cancelledAt == null) {
            throw new IllegalArgumentException("cancelled_at обязателен для статуса cancelled");
        }
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    public void move(Instant newStartsAt, Instant newEndsAt) {
        if (!newEndsAt.isAfter(newStartsAt)) {
            throw new IllegalArgumentException("Окончание брони должно быть позже начала");
        }
        this.startsAt = newStartsAt;
        this.endsAt = newEndsAt;
        this.status = BookingStatus.MOVED;
    }
}
