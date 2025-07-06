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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceVerificationService {

    private final PythonIntegrationService pythonIntegrationService;
    private final FileStorageService fileStorageService;

    public FaceMatchResult verifyFaceMatch(String selfieUrl, String licensePhotoUrl) {
        log.info("Performing face verification between selfie and license photo using FastAPI");
        log.debug("Selfie URL: {}, License URL: {}", selfieUrl, licensePhotoUrl);

        try {
            // Download images to temporary files for Python processing
            Path tempSelfie = downloadImageToTempFile(selfieUrl, "selfie");
            Path tempLicense = downloadImageToTempFile(licensePhotoUrl, "license");

            log.info("Downloaded images to temp files: {} and {}", tempSelfie, tempLicense);

            try {
                // Call Python script for face verification
                JsonNode result = pythonIntegrationService.executePythonScript(
                        "face_verification.py",
                        tempSelfie.toString(),
                        tempLicense.toString()
                );

                // Parse Python result
                FaceVerificationResponse response = parsePythonFaceResult(result);

                log.info("Face verification completed. Confidence: {}, Match: {}",
                        response.getConfidenceScore(), response.isMatch());

                return FaceMatchResult.builder()
                        .match(response.isMatch()) // Fixed: using match instead of isMatch
                        .confidenceScore(response.getConfidenceScore())
                        .selfieUrl(selfieUrl)
                        .licensePhotoUrl(licensePhotoUrl)
                        .verificationMethod("FASTAPI_AI")
                        .distance(response.getDistance())
                        .threshold(response.getThreshold())
                        .model(response.getModel())
                        .errorMessage(response.getErrorMessage())
                        .build();

            } finally {
                // Clean up temporary files
                cleanupTempFile(tempSelfie);
                cleanupTempFile(tempLicense);
            }

        } catch (Exception e) {
            log.error("Face verification failed: {}", e.getMessage(), e);
            return FaceMatchResult.builder()
                    .match(false) // Fixed: using match instead of isMatch
                    .confidenceScore(0.0)
                    .selfieUrl(selfieUrl)
                    .licensePhotoUrl(licensePhotoUrl)
                    .verificationMethod("FASTAPI_AI")
                    .errorMessage("Face verification failed: " + e.getMessage())
                    .build();
        }
    }

    private Path downloadImageToTempFile(String imageUrl, String prefix) throws IOException {
        log.debug("Downloading image from URL: {}", imageUrl);

        // Create temp file
        Path tempFile = Files.createTempFile(prefix + "_" + UUID.randomUUID(), ".jpg");

        // Download file content
        byte[] imageBytes = fileStorageService.downloadFile(imageUrl);

        // Write to temp file
        Files.write(tempFile, imageBytes);

        log.debug("Image downloaded to temp file: {}", tempFile);
        return tempFile;
    }

    private void cleanupTempFile(Path tempFile) {
        try {
            if (tempFile != null && Files.exists(tempFile)) {
                Files.delete(tempFile);
                log.debug("Cleaned up temp file: {}", tempFile);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp file {}: {}", tempFile, e.getMessage());
        }
    }

    private FaceVerificationResponse parsePythonFaceResult(JsonNode result) {
        try {
            log.debug("Parsing Python face verification result: {}", result);

            boolean success = result.get("success").asBoolean(false);
            boolean isMatch = result.get("verified").asBoolean(false); // Note: FastAPI returns 'verified'
            double confidenceScore = result.get("similarity_score").asDouble(0.0); // Note: FastAPI returns 'similarity_score'

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
        private Boolean match; // Changed from isMatch to match to fix access issues
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