package ru.ssau.rbs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.ssau.rbs.domain.enums.ResourceStatus;
import ru.ssau.rbs.domain.enums.ResourceType;

@Entity
@Table(name = "resources")
@Getter
@Setter
@NoArgsConstructor
public class Resource extends AuditableEntity {

    @Column(name = "code", nullable = false, unique = true, columnDefinition = "text")
    private String code;

    @Column(name = "calendar_code", unique = true, columnDefinition = "text")
    private String calendarCode;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "type", nullable = false)
    private ResourceType type;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "location", nullable = false, columnDefinition = "text")
    private String location;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "features", nullable = false, columnDefinition = "text[]")
    private String[] features = new String[0];

    @Column(name = "timezone", nullable = false)
    private String timeZone = "Europe/Samara";

    @Column(name = "status", nullable = false)
    private ResourceStatus status = ResourceStatus.ACTIVE;

    public boolean isActive() {
        return status == ResourceStatus.ACTIVE;
    }

    public void archive() {
        this.status = ResourceStatus.ARCHIVED;
    }

    public void activate() {
        this.status = ResourceStatus.ACTIVE;
    }
}
