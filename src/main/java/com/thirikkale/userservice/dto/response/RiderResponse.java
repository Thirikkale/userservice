package com.thirikkale.userservice.dto.response;

import com.thirikkale.userservice.model.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiderResponse {

    private UUID riderId;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;

    // Optional fields that can be null initially
    private LocalDate dateOfBirth;
    private String profilePhotoUrl;
    private String emergencyContactName;
    private String emergencyContactPhone;

    // Gender detection fields (optional)
    private Gender gender;
    private Boolean womenOnlyAccess;
    private Boolean genderVerified;
    private String selfieUrl;

    // Ride-related fields
    private Double rating;
    private Integer totalRides;
    private LocalDateTime lastRideDate;
    private String preferredPaymentMethod;

    // Status fields
    private Boolean isActive;
    private Boolean isPhoneVerified;
    private LocalDateTime createdAt;

    // Helper method to check if profile is complete
    public boolean isProfileComplete() {
        return dateOfBirth != null &&
                emergencyContactName != null &&
                emergencyContactPhone != null;
    }

    // Helper method to check if gender detection is done
    public boolean isGenderDetectionDone() {
        return genderVerified != null && genderVerified;
    }
}