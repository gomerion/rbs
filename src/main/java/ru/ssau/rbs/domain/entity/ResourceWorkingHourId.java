package ru.ssau.rbs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalTime;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ResourceWorkingHourId implements Serializable {

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "weekday", nullable = false)
    private Short weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
}
