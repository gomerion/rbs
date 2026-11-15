package ru.ssau.rbs.dto.resource;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkingHoursDto {

    @NotNull
    @Min(1)
    @Max(7)
    private Integer weekday;

    @NotNull
    @JsonProperty("start_time")
    private LocalTime startTime;

    @NotNull
    @JsonProperty("end_time")
    private LocalTime endTime;
}
