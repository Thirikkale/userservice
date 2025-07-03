package com.thirikkale.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponse {

    private boolean success;
    private String message;
    private String phoneNumber;
    private int expiresInMinutes;
    private int attemptsRemaining;

    public static OtpResponse success(String phoneNumber, int expiresInMinutes) {
        return OtpResponse.builder()
                .success(true)
                .message("OTP sent successfully")
                .phoneNumber(phoneNumber)
                .expiresInMinutes(expiresInMinutes)
                .attemptsRemaining(3)
                .build();
    }

    public static OtpResponse failure(String message) {
        return OtpResponse.builder()
                .success(false)
                .message(message)
                .attemptsRemaining(0)
                .build();
    }
}