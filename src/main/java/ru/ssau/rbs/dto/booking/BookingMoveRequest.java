package ru.ssau.rbs.dto.booking;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingMoveRequest {

    @NotNull
    @JsonProperty("starts_at")
    private Instant startsAt;

    @NotNull
    @JsonProperty("ends_at")
    private Instant endsAt;
}
