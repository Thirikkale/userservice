package com.thirikkale.userservice.model;

import com.thirikkale.userservice.model.enums.VehicleType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", referencedColumnName = "driver_id")
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "vehicle_registration", unique = true)
    private String vehicleRegistration;

    @Column(name = "vehicle_model")
    private String vehicleModel;

    @Column(name = "vehicle_year")
    private String vehicleYear;

    @Column(name = "vehicle_color")
    private String vehicleColor;

    @Column(name = "vehicle_make")
    private String vehicleMake;

    // Vehicle status
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_documents_uploaded")
    @Builder.Default
    private Boolean isDocumentsUploaded = false;

    @Column(name = "verification_status")
    @Builder.Default
    private String verificationStatus = "PENDING"; // PENDING, IN_PROGRESS, VERIFIED, REJECTED

    // Document URLs for this specific vehicle
    @Column(name = "revenue_license_url")
    private String revenueLicenseUrl;

    @Column(name = "vehicle_registration_url")
    private String vehicleRegistrationUrl;

    @Column(name = "vehicle_insurance_url")
    private String vehicleInsuranceUrl;

    // Insurance details
    @Column(name = "insurance_company")
    private String insuranceCompany;

    @Column(name = "insurance_policy_number")
    private String insurancePolicyNumber;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "revenue_license_expiry")
    private LocalDate revenueLicenseExpiry;

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
        return isVerified && "VERIFIED".equals(verificationStatus) && isDocumentsUploaded;
    }

    public int getVerificationProgress() {
        int progress = 0;
        if (revenueLicenseUrl != null) progress += 33;
        if (vehicleRegistrationUrl != null) progress += 33;
        if (vehicleInsuranceUrl != null) progress += 34;
        return progress;
    }
}