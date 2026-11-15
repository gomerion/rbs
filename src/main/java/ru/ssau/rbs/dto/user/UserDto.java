package ru.ssau.rbs.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserDto {

    private Long id;
    private String email;
    private String role;

    @JsonProperty("department_id")
    private Long departmentId;
}
