package com.thirikkale.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLoginRequest {

    @NotBlank(message = "Email or phone number is required")
    private String emailOrPhone;

    @NotBlank(message = "Password is required")
    private String password;
}