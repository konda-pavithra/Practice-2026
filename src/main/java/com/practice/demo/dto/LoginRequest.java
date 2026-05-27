package com.practice.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Credentials for user login")
@Data
public class LoginRequest {

    @Schema(description = "Registered username", example = "john_doe")
    private String username;

    @Schema(description = "Account password", example = "Secret@123")
    private String password;
}
