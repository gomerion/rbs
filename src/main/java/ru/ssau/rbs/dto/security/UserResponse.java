package ru.ssau.rbs.dto.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.ssau.rbs.domain.enums.UserRole;

@Data 
@AllArgsConstructor 
public class UserResponse {

    private Long id;
    private String email;
    private UserRole role;
    
}
