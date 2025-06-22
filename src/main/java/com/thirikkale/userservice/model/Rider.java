package com.thirikkale.userservice.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "riders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rider {

    @Id
    @Column(name = "rider_id")
    private UUID riderId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "rider_id")
    private User user;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "total_rides")
    @Builder.Default
    private Integer totalRides = 0;

    @Column(name = "last_ride_date")
    private LocalDateTime lastRideDate;

    @Column(name = "preferred_payment_method")
    @Builder.Default
    private String preferredPaymentMethod = "CASH";

    @Column(name = "women_only_preference")
    @Builder.Default
    private Boolean womenOnlyPreference = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}