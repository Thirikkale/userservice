package com.thirikkale.userservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.thirikkale.userservice.dto.response.FaceVerificationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceVerificationService {

    private final PythonIntegrationService pythonIntegrationService;
    private final FileStorageService fileStorageService;

    public FaceMatchResult verifyFaceMatch(String selfieAbsolutePath, String licenseAbsolutePath) {
        log.info("Performing face verification using absolute paths");
        log.debug("Selfie: {}, License: {}", selfieAbsolutePath, licenseAbsolutePath);

        try {
            // Verify files exist
            if (!Files.exists(Paths.get(selfieAbsolutePath))) {
                throw new IOException("Selfie file not found: " + selfieAbsolutePath);
            }

            if (!Files.exists(Paths.get(licenseAbsolutePath))) {
                throw new IOException("License file not found: " + licenseAbsolutePath);
            }

            log.info("Both files exist, calling Python service for face verification");

            // Call Python script for face verification using absolute paths
            JsonNode result = pythonIntegrationService.executePythonScript(
                    "face_verification.py",
                    selfieAbsolutePath,
                    licenseAbsolutePath
            );

            // Parse Python result
            FaceVerificationResponse response = parsePythonFaceResult(result);

            log.info("Face verification completed. Confidence: {}, Match: {}",
                    response.getConfidenceScore(), response.isMatch());

            return FaceMatchResult.builder()
                    .match(response.isMatch())
                    .confidenceScore(response.getConfidenceScore())
                    .selfieUrl(selfieAbsolutePath)
                    .licensePhotoUrl(licenseAbsolutePath)
                    .verificationMethod("FASTAPI_AI")
                    .distance(response.getDistance())
                    .threshold(response.getThreshold())
                    .model(response.getModel())
                    .errorMessage(response.getErrorMessage())
                    .build();

        } catch (Exception e) {
            log.error("Face verification failed: {}", e.getMessage(), e);
            return FaceMatchResult.builder()
                    .match(false)
                    .confidenceScore(0.0)
                    .selfieUrl(selfieAbsolutePath)
                    .licensePhotoUrl(licenseAbsolutePath)
                    .verificationMethod("FASTAPI_AI")
                    .errorMessage("Face verification failed: " + e.getMessage())
                    .build();
        }
    }

    private FaceVerificationResponse parsePythonFaceResult(JsonNode result) {
        try {
            log.debug("Parsing Python face verification result: {}", result);

            boolean success = result.get("success").asBoolean(false);
            boolean isMatch = result.get("verified").asBoolean(false);
            double confidenceScore = result.get("similarity_score").asDouble(0.0);

            String errorMessage = null;
            if (!success && result.has("error")) {
                errorMessage = result.get("error").asText();
            }

            // Optional fields with proper fallbacks
            double distance = result.has("distance") ? result.get("distance").asDouble(0.0) : 0.0;
            double threshold = result.has("threshold") ? result.get("threshold").asDouble(0.6) : 0.6;
            String model = result.has("model") ? result.get("model").asText("FastAPI") : "FastAPI";

            log.info("Parsed verification result: success={}, match={}, confidence={}",
                    success, isMatch, confidenceScore);

            return FaceVerificationResponse.builder()
                    .success(success)
                    .match(isMatch)
                    .confidenceScore(confidenceScore)
                    .distance(distance)
                    .threshold(threshold)
                    .model(model)
                    .errorMessage(errorMessage)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Python face verification result: {}", e.getMessage());
            return FaceVerificationResponse.builder()
                    .success(false)
                    .match(false)
                    .confidenceScore(0.0)
                    .errorMessage("Failed to parse face verification result: " + e.getMessage())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaceMatchResult {
        private Boolean match;
        private Double confidenceScore;
        private String selfieUrl;
        private String licensePhotoUrl;
        private String verificationMethod;
        private Double distance;
        private Double threshold;
        private String model;
        private String errorMessage;

        // Add helper method for backward compatibility
        public Boolean isMatch() {
            return match;
        }

        public Boolean getIsMatch() {
            return match;
        }
    }
}