package ru.ssau.rbs.dto.audit;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;

    @JsonProperty("actor_id")
    private Long actorId;

    private String action;

    @JsonProperty("object_type")
    private String objectType;

    @JsonProperty("object_id")
    private String objectId;

    @JsonProperty("before_state")
    private Map<String, Object> beforeState;

    @JsonProperty("after_state")
    private Map<String, Object> afterState;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("created_at")
    private Instant createdAt;
}
