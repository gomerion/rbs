package ru.ssau.rbs.dto.booking;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreateRequest {

    @NotNull
    @JsonProperty("resource_id")
    private Long resourceId;

    @NotNull
    @JsonProperty("starts_at")
    private Instant startsAt;

    @NotNull
    @JsonProperty("ends_at")
    private Instant endsAt;

    @NotBlank
    @Size(max = 500)
    private String purpose;

    @JsonProperty("user_id")
    private Long userId;
}
