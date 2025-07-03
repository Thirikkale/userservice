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
public class FaceVerificationResponse {

    private UUID driverId;
    private Boolean match; // Changed from isMatch to match to avoid boolean getter issues
    private Double confidenceScore;
    private String verificationStatus; // VERIFIED, FAILED, MANUAL_REVIEW, IN_PROGRESS
    private String message;
    private Integer attemptNumber;
    private String selfieUrl;
    private String licensePhotoUrl;
    private String verificationMethod;

    // Additional fields for Python integration
    private Boolean success;
    private Double distance;
    private Double threshold;
    private String model;
    private String errorMessage;

    // Helper method for backward compatibility
    public Boolean isMatch() {
        return match;
    }

    public void setMatch(Boolean match) {
        this.match = match;
    }

    public static FaceVerificationResponse success(UUID driverId, Double confidenceScore,
            String selfieUrl, String licensePhotoUrl,
            Integer attemptNumber) {
        return FaceVerificationResponse.builder()
                .driverId(driverId)
                .match(true)
                .confidenceScore(confidenceScore)
                .verificationStatus("VERIFIED")
                .message("Face verification successful")
                .attemptNumber(attemptNumber)
                .selfieUrl(selfieUrl)
                .licensePhotoUrl(licensePhotoUrl)
                .verificationMethod("DEEPFACE_AI")
                .success(true)
                .build();
    }

    public static FaceVerificationResponse failure(UUID driverId, Double confidenceScore,
            String message, Integer attemptNumber) {
        return FaceVerificationResponse.builder()
                .driverId(driverId)
                .match(false)
                .confidenceScore(confidenceScore)
                .verificationStatus("FAILED")
                .message(message)
                .attemptNumber(attemptNumber)
                .verificationMethod("DEEPFACE_AI")
                .success(false)
                .build();
    }

    public static FaceVerificationResponse manualReview(UUID driverId, Double confidenceScore,
            Integer attemptNumber) {
        return FaceVerificationResponse.builder()
                .driverId(driverId)
                .match(false)
                .confidenceScore(confidenceScore)
                .verificationStatus("MANUAL_REVIEW")
                .message("Face verification requires manual review by support team")
                .attemptNumber(attemptNumber)
                .verificationMethod("DEEPFACE_AI")
                .success(true)
                .build();
    }
}
