package ru.ssau.rbs.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UtilizationItem {

    private String key;
    private long bookings;

    @JsonProperty("busy_hours")
    private double busyHours;

    @JsonProperty("utilization_ratio")
    private double utilizationRatio;
}
