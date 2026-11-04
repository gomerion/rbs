package ru.ssau.rbs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "resource_working_hours")
@Getter
@Setter
@NoArgsConstructor
public class ResourceWorkingHour {

    @EmbeddedId
    private ResourceWorkingHourId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("resourceId")
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public boolean isValidWindow() {
        return id != null && id.getStartTime() != null && endTime != null
                && id.getStartTime().isBefore(endTime);
    }
}
