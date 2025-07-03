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

    // Add the missing static factory method
    public static AuthResponse of(String token, long expiresIn, UUID userId, String email, String firstName, String lastName) {
        return AuthResponse.builder()
                .userId(userId)
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .loginTime(LocalDateTime.now())
                .build();
    }
}