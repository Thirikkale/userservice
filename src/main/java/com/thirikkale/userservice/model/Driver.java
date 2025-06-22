package com.thirikkale.userservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "drivers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @Column(name = "driver_id")
    private UUID driverId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "driver_id")
    private User user;

    @Column(name = "is_available")
    @Builder.Default
    private Boolean isAvailable = false;

    @Column(name = "verification_date")
    private LocalDateTime verificationDate;

    @Column(name = "bank_ac_no")
    private String bankAccountNumber;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "ifsc_code")
    private String ifscCode;

    @Column(name = "total_earnings", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @NotNull
    @Column(name = "license_number", unique = true)
    private String licenseNumber;

    @NotNull
    @Column(name = "license_expiry")
    private LocalDate licenseExpiry;

    @NotNull
    @Column(name = "vehicle_registration", unique = true)
    private String vehicleRegistration;

    @Column(name = "total_rides_completed")
    @Builder.Default
    private Integer totalRidesCompleted = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}