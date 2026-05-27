package com.practice.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Payload for creating a new user account")
@Data
public class RegistrationRequest {

    @Schema(description = "Unique username (3–30 characters, letters/digits/underscores)",
            example = "john_doe")
    private String username;

    @Schema(description = "Valid email address — used for price-alert notifications",
            example = "john.doe@example.com")
    private String email;

    @Schema(description = "Password (minimum 8 characters, must include at least one digit)",
            example = "Secret@123")
    private String password;
}
