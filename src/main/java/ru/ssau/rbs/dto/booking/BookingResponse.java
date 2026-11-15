package ru.ssau.rbs.dto.booking;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BookingResponse {

    private Long id;

    @JsonProperty("resource_id")
    private Long resourceId;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("created_by_id")
    private Long createdById;

    @JsonProperty("starts_at")
    private Instant startsAt;

    @JsonProperty("ends_at")
    private Instant endsAt;

    private String status;
    private String purpose;

    @JsonProperty("sync_status")
    private String syncStatus;

    @JsonProperty("created_at")
    private Instant createdAt;
}
