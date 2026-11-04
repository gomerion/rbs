package ru.ssau.rbs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;

import java.time.Instant;

@Getter
@MappedSuperclass
public abstract class CreatedAtEntity extends AbstractEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPrePersist() {
        this.createdAt = Instant.now();
        onCreated();
    }

    protected void onCreated() {
    }
}
