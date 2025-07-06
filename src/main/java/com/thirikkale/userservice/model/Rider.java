package com.thirikkale.userservice.model;

import com.thirikkale.userservice.model.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "riders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Rider {

    @Id
    @Column(name = "rider_id")
    private UUID riderId; // TPT: This will be set manually to match User's userId

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REFRESH)
    @JoinColumn(name = "rider_id", referencedColumnName = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Gender gender = Gender.NOT_SPECIFIED;

    @Column(name = "selfie_url")
    private String selfieUrl;

    @Column(name = "women_only_access")
    @Builder.Default
    private Boolean womenOnlyAccess = false;

    @Column(name = "gender_verified")
    @Builder.Default
    private Boolean genderVerified = false;

    @Column(name = "rating")
    @Builder.Default
    private Double rating = 0.0;

    @Column(name = "total_rides")
    @Builder.Default
    private Integer totalRides = 0;

    @Column(name = "last_ride_date")
    private LocalDateTime lastRideDate;

    @Column(name = "preferred_payment_method")
    @Builder.Default
    private String preferredPaymentMethod = "CASH";

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

        // Set default values only if they are null
        if (this.gender == null) {
            this.gender = Gender.NOT_SPECIFIED;
        }
        if (this.genderVerified == null) {
            this.genderVerified = false;
        }
        if (this.totalRides == null) {
            this.totalRides = 0;
        }
        if (this.rating == null) {
            this.rating = 0.0;
        }
        if (this.womenOnlyAccess == null) {
            this.womenOnlyAccess = false;
        }
        if (this.preferredPaymentMethod == null) {
            this.preferredPaymentMethod = "CASH";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}