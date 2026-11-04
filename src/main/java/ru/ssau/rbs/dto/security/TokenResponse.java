package ru.ssau.rbs.dto.security;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data 
@AllArgsConstructor 
public class TokenResponse {

    @JsonProperty("access_token") 
    private String accessToken;
    @JsonProperty("token_type") 
    private String tokenType;
    @JsonProperty("expires_in") 
    private Long expiresIn;

}
