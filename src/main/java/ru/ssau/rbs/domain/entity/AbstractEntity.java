package ru.ssau.rbs.domain.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class AbstractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || !getClass().equals(other.getClass())) {
            return false;
        }
        AbstractEntity that = (AbstractEntity) other;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
