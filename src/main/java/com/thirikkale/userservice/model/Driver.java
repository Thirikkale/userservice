package com.thirikkale.userservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private UUID driverId; // TPT: This will be set manually to match User's userId

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REFRESH)
    @JoinColumn(name = "driver_id", referencedColumnName = "user_id")
    private User user;

    @Column(name = "is_available")
    @Builder.Default
    private Boolean isAvailable = false;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_documents_uploaded")
    @Builder.Default
    private Boolean isDocumentsUploaded = false;

    // Verification status fields
    @Column(name = "face_verification_status")
    @Builder.Default
    private String faceVerificationStatus = "PENDING";

    @Column(name = "document_verification_status")
    @Builder.Default
    private String documentVerificationStatus = "PENDING";

    @Column(name = "profile_extraction_status")
    @Builder.Default
    private String profileExtractionStatus = "PENDING";

    @Column(name = "verification_date")
    private LocalDateTime verificationDate;

    // License information (extracted from driving license)
    @Column(name = "license_number", unique = true)
    private String licenseNumber;

    @Column(name = "license_expiry")
    private LocalDate licenseExpiry;

    // Vehicle information
    @Column(name = "vehicle_registration", unique = true)
    private String vehicleRegistration;

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

    // Document URLs (stored after upload)
    @Column(name = "selfie_url")
    private String selfieUrl;

    @Column(name = "driving_license_url")
    private String drivingLicenseUrl;

    @Column(name = "revenue_license_url")
    private String revenueLicenseUrl;

    @Column(name = "vehicle_registration_url")
    private String vehicleRegistrationUrl;

    @Column(name = "vehicle_insurance_url")
    private String vehicleInsuranceUrl;

    // Face verification scores
    @Column(name = "face_match_score")
    private Double faceMatchScore;

    @Column(name = "face_verification_attempts")
    @Builder.Default
    private Integer faceVerificationAttempts = 0;

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
                "VERIFIED".equals(documentVerificationStatus) &&
                "COMPLETED".equals(profileExtractionStatus);
    }

    public boolean canGoOnline() {
        return isFullyVerified() && isDocumentsUploaded;
    }

    public int getVerificationProgress() {
        int progress = 0;
        if (isDocumentsUploaded)
            progress += 25;
        if ("COMPLETED".equals(profileExtractionStatus))
            progress += 25;
        if ("VERIFIED".equals(documentVerificationStatus))
            progress += 25;
        if ("VERIFIED".equals(faceVerificationStatus))
            progress += 25;
        return progress;
    }
}