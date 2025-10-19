package com.thirikkale.userservice.model;

import com.thirikkale.userservice.model.enums.AdminRoleType;
import com.thirikkale.userservice.model.enums.AdminStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin Entity - Separate from Users
 * Represents system administrators who manage the Thirikkale platform
 * Users (Drivers & Riders) are managed in the Users table
 */
@Entity
@Table(name = "admins", indexes = {
        @Index(name = "idx_admin_email", columnList = "email", unique = true),
        @Index(name = "idx_admin_phone", columnList = "phone_number", unique = true),
        @Index(name = "idx_admin_role", columnList = "admin_role"),
        @Index(name = "idx_admin_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "admin_id")
    private UUID adminId;

    @NotBlank(message = "First name is required")
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Email(message = "Email should be valid")
    @NotBlank(message = "Email is required")
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @NotBlank(message = "Phone number is required")
    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Column(name = "password", nullable = false)
    private String password; // Hashed password

    @NotNull(message = "Admin role is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "admin_role", nullable = false, length = 50)
    private AdminRoleType adminRole;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private AdminStatus status = AdminStatus.PENDING_ACTIVATION;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @Column(name = "email_verification_token_expiry")
    private LocalDateTime emailVerificationTokenExpiry;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    // Version for optimistic locking
    @Version
    private Long version;

    // Helper methods
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return status == AdminStatus.ACTIVATED || status == AdminStatus.ONLINE;
    }

    public boolean isOnline() {
        return status == AdminStatus.ONLINE;
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = AdminStatus.PENDING_ACTIVATION;
        }
    }
}
