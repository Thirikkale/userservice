package com.thirikkale.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverRegistrationRequest {

    @NotBlank(message = "Firebase ID token is required")
    private String firebaseIdToken;

    // Optional fields - can be added later via profile update
    private String firstName;
    private String lastName;
    private String whatsappNumber;
}