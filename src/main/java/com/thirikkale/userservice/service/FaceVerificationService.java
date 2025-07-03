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
        log.info("Performing face verification between selfie and license photo using DeepFace");

        try {
            // Download images to temporary files for Python processing
            Path tempSelfie = downloadImageToTempFile(selfieUrl, "selfie");
            Path tempLicense = downloadImageToTempFile(licensePhotoUrl, "license");

            try {
                // Call Python script for face verification
                JsonNode result = pythonIntegrationService.executePythonScript(
                        "face_verification.py",
                        tempSelfie.toString(),
                        tempLicense.toString()); // Parse Python result
                FaceVerificationResponse response = parsePythonFaceResult(result);

                log.info("Face verification completed. Confidence: {}, Match: {}",
                        response.getConfidenceScore(), response.isMatch());

                return FaceMatchResult.builder()
                        .isMatch(response.isMatch())
                        .confidenceScore(response.getConfidenceScore())
                        .selfieUrl(selfieUrl)
                        .licensePhotoUrl(licensePhotoUrl)
                        .verificationMethod("DEEPFACE_PYTHON")
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
            log.error("Face verification failed: {}", e.getMessage());
            return FaceMatchResult.builder()
                    .isMatch(false)
                    .confidenceScore(0.0)
                    .selfieUrl(selfieUrl)
                    .licensePhotoUrl(licensePhotoUrl)
                    .verificationMethod("DEEPFACE_PYTHON")
                    .errorMessage("Face verification failed: " + e.getMessage())
                    .build();
        }
    }

    private Path downloadImageToTempFile(String imageUrl, String prefix) throws IOException {
        // Create temporary file
        Path tempFile = Files.createTempFile(prefix + "_" + UUID.randomUUID(), ".jpg");

        // Download image from URL and save to temp file
        byte[] imageBytes = fileStorageService.downloadFile(imageUrl);
        Files.write(tempFile, imageBytes);

        log.debug("Downloaded image from {} to temporary file: {}", imageUrl, tempFile);
        return tempFile;
    }

    private void cleanupTempFile(Path tempFile) {
        try {
            if (tempFile != null && Files.exists(tempFile)) {
                Files.delete(tempFile);
                log.debug("Cleaned up temporary file: {}", tempFile);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temporary file {}: {}", tempFile, e.getMessage());
        }
    }

    private FaceVerificationResponse parsePythonFaceResult(JsonNode result) {
        try {
            boolean success = result.get("success").asBoolean(false);
            boolean isMatch = result.get("is_match").asBoolean(false);
            double confidenceScore = result.get("confidence_score").asDouble(0.0);

            String errorMessage = null;
            if (!success && result.has("error")) {
                errorMessage = result.get("error").asText();
            }

            // Optional fields
            double distance = result.has("distance") ? result.get("distance").asDouble(0.0) : 0.0;
            double threshold = result.has("threshold") ? result.get("threshold").asDouble(0.0) : 0.0;
            String model = result.has("model") ? result.get("model").asText("DeepFace") : "DeepFace";

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
        private boolean isMatch;
        private double confidenceScore;
        private String selfieUrl;
        private String licensePhotoUrl;
        private String verificationMethod;
        private String errorMessage;
        private double distance;
        private double threshold;
        private String model;
    }
}