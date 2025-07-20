package com.thirikkale.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private UUID userId;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private String userType; // RIDER, DRIVER, ADMIN
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;
    private Boolean isActive;
    private Boolean isVerified;
    private LocalDateTime loginTime;

    // Registration status fields
    private Boolean isNewRegistration; // true = new user, false = existing user auto-login
    private String registrationMessage; // Custom message for frontend
    private String nextStep; // What user should do next
    private Boolean isProfileComplete;

    // Helper method to check if profile needs completion
    public boolean needsProfileCompletion() {
        return nextStep != null && nextStep.equals("COMPLETE_PROFILE");
    }

    // Factory method for token-only registration
    public static AuthResponse tokenOnlyRegistration(UUID userId, String accessToken, String refreshToken,
                                                     String userType, String phoneNumber, String email) {
        return AuthResponse.builder()
                .userId(userId)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userType(userType)
                .firstName("User") // Placeholder
                .lastName("Profile") // Placeholder
                .phoneNumber(phoneNumber)
                .email(email)
                .isActive(true)
                .isVerified(true)
                .loginTime(LocalDateTime.now())
                .isNewRegistration(true)
                .isProfileComplete(false)
                .registrationMessage("Registration successful! Please complete your profile.")
                .nextStep("COMPLETE_PROFILE")
                .build();
    }

    // Factory method for new registration
    public static AuthResponse newRegistration(UUID userId, String accessToken, String refreshToken,
                                               String userType, String firstName, String lastName,
                                               String phoneNumber, String email, String nextStep) {
        return AuthResponse.builder()
                .userId(userId)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userType(userType)
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber(phoneNumber)
                .email(email)
                .isActive(true)
                .isVerified(true)
                .loginTime(LocalDateTime.now())
                .isNewRegistration(true)
                .registrationMessage("Registration successful! Welcome to Thirikkale.")
                .nextStep(nextStep)
                .build();
    }

    // Factory method for auto-login
    public static AuthResponse autoLogin(UUID userId, String accessToken, String refreshToken,
                                         String userType, String firstName, String lastName,
                                         String phoneNumber, String email, String welcomeMessage) {
        return AuthResponse.builder()
                .userId(userId)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userType(userType)
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber(phoneNumber)
                .email(email)
                .isActive(true)
                .isVerified(true)
                .loginTime(LocalDateTime.now())
                .isNewRegistration(false)
                .registrationMessage(welcomeMessage)
                .nextStep("You're all set! Start using the app.")
                .build();
    }
}