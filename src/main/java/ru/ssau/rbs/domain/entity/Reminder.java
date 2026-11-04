package ru.ssau.rbs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.ssau.rbs.domain.enums.ReminderStatus;

import java.time.Instant;

@Entity
@Table(name = "reminders")
@Getter
@Setter
@NoArgsConstructor
public class Reminder extends CreatedAtEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "remind_at", nullable = false)
    private Instant remindAt;

    @Column(name = "channel", nullable = false)
    private String channel = "log";

    @Column(name = "status", nullable = false)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "sent_at")
    private Instant sentAt;

    public void markSent(Instant sentAt) {
        this.status = ReminderStatus.SENT;
        this.sentAt = sentAt;
        this.attempts++;
    }

    public void markFailed() {
        this.status = ReminderStatus.FAILED;
        this.attempts++;
    }
}
