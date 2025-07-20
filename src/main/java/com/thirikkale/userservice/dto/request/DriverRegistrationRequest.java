package com.thirikkale.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DriverRegistrationRequest {

    @NotBlank(message = "Firebase ID token is required")
    private String firebaseIdToken;

    // Remove firstName, lastName, and whatsappNumber - they'll be in a separate request
}