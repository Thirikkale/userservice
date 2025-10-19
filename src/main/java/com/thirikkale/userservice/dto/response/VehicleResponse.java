package com.thirikkale.userservice.dto.response;

import com.thirikkale.userservice.model.enums.VehicleType;
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
public class VehicleResponse {

    private UUID vehicleId;
    private String readableId; // Human-readable ID (e.g., V00001, V00002)
    private UUID driverId;
    private String driverReadableId; // Driver's readable ID (e.g., D00001)
    private VehicleType vehicleType;
    private String vehicleRegistration;
    private String vehicleModel;
    private String vehicleYear;
    private String vehicleColor;
    private String vehicleMake;

    // Status fields
    private Boolean isActive;
    private Boolean isVerified;
    private Boolean isDocumentsUploaded;
    private String verificationStatus;
    private int verificationProgress;

    // Document URLs
    private String revenueLicenseUrl;
    private String vehicleRegistrationUrl;
    private String vehicleInsuranceUrl;

    // Insurance details
    private String insuranceCompany;
    private String insurancePolicyNumber;
    private LocalDate insuranceExpiry;
    private LocalDate revenueLicenseExpiry;

    private Boolean isPrimary;
    private LocalDateTime createdAt;

    // Helper methods
    public String getNextRequiredAction() {
        if (!isDocumentsUploaded) {
            return "Upload vehicle documents";
        } else if ("PENDING".equals(verificationStatus)) {
            return "Document verification in progress";
        } else if ("REJECTED".equals(verificationStatus)) {
            return "Document verification failed - please re-upload";
        } else if (isVerified) {
            return "Vehicle verification complete";
        } else {
            return "Verification in progress";
        }
    }
}