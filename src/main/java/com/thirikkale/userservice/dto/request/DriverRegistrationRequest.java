package com.thirikkale.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DriverRegistrationRequest {

    @NotBlank(message = "Firebase ID token is required")
    private String firebaseIdToken;

    // Optional fields - can be extracted from OCR later
    private String firstName;
    private String lastName;
    private String whatsappNumber;
}