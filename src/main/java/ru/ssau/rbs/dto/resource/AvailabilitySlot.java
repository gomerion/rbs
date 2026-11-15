package ru.ssau.rbs.dto.resource;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AvailabilitySlot {

    private Instant start;
    private Instant end;
}
