package com.thirikkale.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vehicle Response DTO specifically for Admin API
 * Returns all fields as Strings for easier frontend consumption
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVehicleResponse {
    private String vehicleId;
    private String readableId; // V00001, V00002 - for display
    private String driverId;
    private String driverReadableId; // D00001 - for display
    private String driverName;
    private String vehicleType;
    private String vehicleRegistration;
    private String vehicleModel;
    private String vehicleYear;
    private String vehicleColor;
    private String vehicleMake;
    private Boolean isActive;
    private Boolean isVerified;
    private Boolean isDocumentsUploaded;
    private String verificationStatus;
    private String revenueLicenseUrl;
    private String vehicleRegistrationUrl;
    private String vehicleInsuranceUrl;
    private String revenueLicenseVerificationStatus;
    private String vehicleRegistrationVerificationStatus;
    private String vehicleInsuranceVerificationStatus;
    private String insuranceCompany;
    private String insurancePolicyNumber;
    private String insuranceExpiry;
    private String revenueLicenseExpiry;
    private String createdAt;
}
