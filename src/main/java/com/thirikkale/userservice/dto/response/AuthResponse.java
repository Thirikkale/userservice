package com.thirikkale.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;

    public static AuthResponse of(String accessToken, long expiresIn, UUID userId,
                                  String email, String firstName, String lastName) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .userId(userId)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .build();
    }
}