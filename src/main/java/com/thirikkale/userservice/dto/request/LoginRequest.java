package com.thirikkale.userservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login request with email/phone and password")
public class LoginRequest {

    @NotBlank(message = "Email or phone number is required")
    @Schema(description = "Email address or phone number", example = "+94771234567")
    private String emailOrPhone;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    @Schema(description = "User password", example = "password123")
    private String password;
}