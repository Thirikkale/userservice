package com.thirikkale.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RiderRegistrationRequest {

    @NotBlank(message = "Firebase ID token is required")
    private String firebaseIdToken;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    private String lastName;

    // Optional fields - can be null during registration
    // These will be updated later via profile update endpoints
}