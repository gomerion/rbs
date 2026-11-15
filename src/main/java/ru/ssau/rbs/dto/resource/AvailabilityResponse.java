package ru.ssau.rbs.dto.resource;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AvailabilityResponse {

    @JsonProperty("resource_id")
    private Long resourceId;

    private List<AvailabilitySlot> items;
}
