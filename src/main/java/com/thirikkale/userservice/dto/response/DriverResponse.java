package com.thirikkale.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverResponse {

    private UUID driverId;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;
    private LocalDate dateOfBirth;
    private String profilePhotoUrl;

    // Availability and verification status
    private Boolean isAvailable;
    private Boolean isVerified;
    private Boolean isDocumentsUploaded;

    // Detailed verification statuses
    private String faceVerificationStatus; // PENDING, IN_PROGRESS, VERIFIED, FAILED, MANUAL_REVIEW
    private String documentVerificationStatus; // PENDING, IN_PROGRESS, VERIFIED, REJECTED
    private String profileExtractionStatus; // PENDING, IN_PROGRESS, COMPLETED, FAILED

    private LocalDateTime verificationDate;
    private int verificationProgress; // 0-100%

    // License and vehicle information
    private String licenseNumber;
    private LocalDate licenseExpiry;
    private String vehicleRegistration;
    private String whatsappNumber;

    // Document URLs
    private String selfieUrl;
    private String drivingLicenseUrl;
    private String revenueLicenseUrl;
    private String vehicleRegistrationUrl;
    private String vehicleInsuranceUrl;

    // Face verification details
    private Double faceMatchScore;
    private Integer faceVerificationAttempts;

    // Performance metrics
    private BigDecimal totalEarnings;
    private Integer totalRidesCompleted;
    private BigDecimal rating;

    // Account status
    private Boolean isActive;
    private Boolean isPhoneVerified;
    private LocalDateTime createdAt;

    // Helper methods for frontend
    public boolean canGoOnline() {
        return isVerified && isDocumentsUploaded &&
                "VERIFIED".equals(faceVerificationStatus) &&
                "VERIFIED".equals(documentVerificationStatus);
    }

    public String getNextRequiredAction() {
        if (!isDocumentsUploaded) {
            return "Upload remaining documents";
        } else if ("PENDING".equals(profileExtractionStatus)) {
            return "Waiting for profile extraction from driving license";
        } else if ("IN_PROGRESS".equals(faceVerificationStatus)) {
            return "Face verification in progress";
        } else if ("MANUAL_REVIEW".equals(faceVerificationStatus)) {
            return "Face verification under manual review";
        } else if ("FAILED".equals(faceVerificationStatus)) {
            return "Face verification failed - please re-upload clear photos";
        } else if (canGoOnline()) {
            return "Verification complete - you can go online";
        } else {
            return "Document verification in progress";
        }
    }

    public boolean requiresManualReview() {
        return "MANUAL_REVIEW".equals(faceVerificationStatus);
    }
}