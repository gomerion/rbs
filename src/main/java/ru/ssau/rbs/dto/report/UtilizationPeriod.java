package ru.ssau.rbs.dto.report;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UtilizationPeriod {

    private Instant from;
    private Instant to;
}
