package ru.ssau.rbs.dto.resource;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    private String type;

    @NotNull
    @Min(0)
    private Integer capacity;

    @NotBlank
    private String location;

    private List<String> features;

    @NotBlank
    @JsonProperty("timezone")
    private String timeZone;

    @Valid
    @JsonProperty("working_hours")
    private List<WorkingHoursDto> workingHours;

    @JsonProperty("calendar_code")
    private String calendarCode;
}
