package com.thirikkale.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RiderRegistrationRequest {

    @NotBlank(message = "Firebase ID token is required")
    private String firebaseIdToken;

    // Remove firstName and lastName - they'll be in a separate request
}