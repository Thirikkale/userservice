package com.thirikkale.userservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RiderProfileUpdateRequest {

    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    private String firstName;

    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    private String lastName;

    @Email(message = "Email should be valid")
    private String email;

    private LocalDate dateOfBirth;

    @Size(min = 2, max = 100, message = "Emergency contact name must be between 2 and 100 characters")
    private String emergencyContactName;

    @Size(min = 10, max = 20, message = "Emergency contact phone must be between 10 and 20 characters")
    private String emergencyContactPhone;

    private String preferredPaymentMethod; // CASH, CARD, MOBILE_WALLET

    // All fields are optional for profile updates
}