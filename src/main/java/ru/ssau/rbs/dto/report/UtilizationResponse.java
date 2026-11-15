package ru.ssau.rbs.dto.report;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UtilizationResponse {

    private UtilizationPeriod period;

    @JsonProperty("group_by")
    private String groupBy;

    private List<UtilizationItem> items;

    private List<UtilizationItem> top5;
}
