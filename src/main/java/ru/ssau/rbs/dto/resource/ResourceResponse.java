package ru.ssau.rbs.dto.resource;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResourceResponse {

    private Long id;
    private String code;
    private String name;
    private String type;
    private int capacity;
    private String location;
    private List<String> features;
    @JsonProperty("timezone")
    private String timeZone;

    @JsonProperty("calendar_code")
    private String calendarCode;

    private String status;

    @JsonProperty("working_hours")
    private List<WorkingHoursDto> workingHours;
}
