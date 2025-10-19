package com.thirikkale.userservice.model;

import com.thirikkale.userservice.model.enums.VehicleType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "drivers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Driver {

    @Id
    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "readable_id", unique = true, length = 20)
    private String readableId; // Human-readable ID (e.g., D00001, D00002)

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REFRESH)
    @JoinColumn(name = "driver_id", referencedColumnName = "user_id")
    private User user;

    // Driver personal documents (not vehicle-specific)
    @Column(name = "selfie_url")
    private String selfieUrl;

    @Column(name = "driving_license_url")
    private String drivingLicenseUrl;

    // Individual document verification statuses for driver documents
    @Column(name = "selfie_verification_status")
    @Builder.Default
    private String selfieVerificationStatus = "PENDING";

    @Column(name = "driving_license_verification_status")
    @Builder.Default
    private String drivingLicenseVerificationStatus = "PENDING";

    // Driver status
    @Column(name = "is_available")
    @Builder.Default
    private Boolean isAvailable = false;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_documents_uploaded")
    @Builder.Default
    private Boolean isDocumentsUploaded = false;

    // Verification status fields for personal documents
    @Column(name = "face_verification_status")
    @Builder.Default
    private String faceVerificationStatus = "PENDING";

    @Column(name = "profile_extraction_status")
    @Builder.Default
    private String profileExtractionStatus = "PENDING";

    @Column(name = "document_verification_status")
    @Builder.Default
    private String documentVerificationStatus = "PENDING";

    @Column(name = "verification_date")
    private LocalDateTime verificationDate;

    // License information (extracted from driving license)
    @Column(name = "license_number", unique = true)
    private String licenseNumber;

    @Column(name = "license_expiry")
    private LocalDate licenseExpiry;

    @Column(name = "whatsapp_number")
    private String whatsappNumber;

    // Bank details
    @Column(name = "bank_ac_no")
    private String bankAccountNumber;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "ifsc_code")
    private String ifscCode;

    // Performance metrics
    @Column(name = "total_earnings", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Column(name = "total_rides_completed")
    @Builder.Default
    private Integer totalRidesCompleted = 0;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    // Face verification scores
    @Column(name = "face_match_score")
    private Double faceMatchScore;

    @Column(name = "face_verification_attempts")
    @Builder.Default
    private Integer faceVerificationAttempts = 0;

    // Vehicles relationship
    @OneToMany(mappedBy = "driver", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Vehicle> vehicles = new ArrayList<>();

    // Primary vehicle (the one currently being used)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_vehicle_id")
    private Vehicle primaryVehicle;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Helper methods
    public boolean isFullyVerified() {
        return isVerified && "VERIFIED".equals(faceVerificationStatus) &&
                "COMPLETED".equals(profileExtractionStatus) &&
                hasAtLeastOneVerifiedVehicle();
    }

    public boolean hasAtLeastOneVerifiedVehicle() {
        return vehicles.stream().anyMatch(Vehicle::isFullyVerified);
    }

    public boolean canGoOnline() {
        return isFullyVerified() && isDocumentsUploaded && primaryVehicle != null;
    }

    public int getVerificationProgress() {
        int progress = 0;

        // Personal documents progress (60% of total)
        if (isDocumentsUploaded)
            progress += 30;
        if ("COMPLETED".equals(profileExtractionStatus))
            progress += 15;
        if ("VERIFIED".equals(faceVerificationStatus))
            progress += 15;

        // Vehicle documents progress (40% of total)
        if (hasAtLeastOneVerifiedVehicle())
            progress += 40;

        return progress;
    }

    public long getVerifiedVehicleCount() {
        return vehicles.stream().mapToLong(v -> v.isFullyVerified() ? 1 : 0).sum();
    }

    public long getTotalVehicleCount() {
        return vehicles.size();
    }

    // Legacy methods for backward compatibility (deprecated - will be removed)
    @Deprecated
    public String getRevenueLicenseUrl() {
        return primaryVehicle != null ? primaryVehicle.getRevenueLicenseUrl() : null;
    }

    @Deprecated
    public String getVehicleRegistrationUrl() {
        return primaryVehicle != null ? primaryVehicle.getVehicleRegistrationUrl() : null;
    }

    @Deprecated
    public String getVehicleInsuranceUrl() {
        return primaryVehicle != null ? primaryVehicle.getVehicleInsuranceUrl() : null;
    }

    @Deprecated
    public String getVehicleRegistration() {
        return primaryVehicle != null ? primaryVehicle.getVehicleRegistration() : null;
    }

}