package ru.ssau.rbs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.Instant;

@Getter
@MappedSuperclass
public abstract class AuditableEntity extends CreatedAtEntity {

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    protected void onCreated() {
        this.updatedAt = getCreatedAt();
    }

    @PreUpdate
    void onPreUpdate() {
        this.updatedAt = Instant.now();
    }
}
