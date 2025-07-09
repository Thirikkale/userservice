package com.thirikkale.userservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.thirikkale.userservice.dto.response.GenderDetectionResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Rider;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.model.enums.Gender;
import com.thirikkale.userservice.repository.RiderRepository;
import com.thirikkale.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenderDetectionService {

    private final RiderRepository riderRepository;
    private final UserRepository userRepository;  // Added UserRepository
    private final FileStorageService fileStorageService;
    private final PythonIntegrationService pythonIntegrationService;

    @Transactional
    public GenderDetectionResponse detectGenderFromSelfie(UUID riderId, MultipartFile selfieFile) {
        log.info("Detecting gender for rider: {}", riderId);

        // Find rider with user
        Rider rider = riderRepository.findByIdWithUser(riderId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Rider not found"));

        User user = rider.getUser();

        try {
            // Store selfie file
            String selfieUrl = fileStorageService.storeFile(selfieFile, "selfies", riderId.toString());

            // Save the selfie to a temporary file for Python processing
            Path tempSelfie = fileStorageService.saveToTempFile(selfieFile, "selfie_" + riderId.toString());

            // FIXED: Use the correct script name that matches PythonIntegrationService
            JsonNode result = pythonIntegrationService.executePythonScript("gender_detection.py", tempSelfie.toString());

            log.info("Python script result: {}", result.toString());

            // Check if the AI service was successful
            boolean success = result.has("success") && result.get("success").asBoolean();
            if (!success) {
                String errorMessage = result.has("error") ? result.get("error").asText() : "Unknown error";
                log.error("AI service failed: {}", errorMessage);
                throw new RuntimeException("AI service failed: " + errorMessage);
            }

            // Parse the result - Updated to handle FastAPI response format
            Gender detectedGender = parseGenderFromResult(result);

            // Extract confidence score - Handle both formats
            double confidence = 0.0;
            if (result.has("confidence_score")) {
                confidence = result.get("confidence_score").asDouble();
            } else if (result.has("confidence")) {
                confidence = result.get("confidence").asDouble();
            }

            log.info("Parsed results - Gender: {}, Confidence: {}", detectedGender, confidence);

            // Update rider information
            rider.setSelfieUrl(selfieUrl);
            rider.setGender(detectedGender);
            rider.setGenderVerified(true);

            // FIXED: Grant women-only access if female detected regardless of confidence
            // Since AI has already verified gender, trust the result
            boolean womenOnlyAccess = (detectedGender == Gender.FEMALE);
            rider.setWomenOnlyAccess(womenOnlyAccess);

            // FIXED: Update user's profile photo URL with the selfie URL
            user.setProfilePhotoUrl(selfieUrl);

            // Save both entities
            userRepository.save(user);
            riderRepository.save(rider);

            // Clean up temporary file
            fileStorageService.deleteTempFile(tempSelfie);

            log.info("Gender detection completed for rider: {} - Gender: {} - Confidence: {} - Women Only Access: {}",
                    riderId, detectedGender, confidence, womenOnlyAccess);

            String responseMessage;
            if (womenOnlyAccess) {
                responseMessage = "Gender verified as female. Women-only rides feature enabled and profile photo updated.";
            } else {
                responseMessage = String.format("Gender detected as %s with %.2f%% confidence. Profile photo updated.",
                        detectedGender.toString().toLowerCase(), confidence * 100);
            }

            return GenderDetectionResponse.builder()
                    .riderId(riderId)
                    .detectedGender(detectedGender)
                    .confidence(confidence)
                    .womenOnlyAccessGranted(womenOnlyAccess)
                    .message(responseMessage)
                    .build();

        } catch (Exception e) {
            log.error("Failed to detect gender for rider {}: {}", riderId, e.getMessage());
            log.error("Exception details: ", e);

            // Fallback: Set gender to NOT_SPECIFIED on error
            rider.setGender(Gender.NOT_SPECIFIED);
            rider.setGenderVerified(false);
            rider.setWomenOnlyAccess(false);
            riderRepository.save(rider);

            return GenderDetectionResponse.builder()
                    .riderId(riderId)
                    .detectedGender(Gender.NOT_SPECIFIED)
                    .confidence(0.0)
                    .womenOnlyAccessGranted(false)
                    .message("Gender detection failed: " + e.getMessage())
                    .build();
        }
    }

    private Gender parseGenderFromResult(JsonNode result) {
        // Handle both old format (gender) and new FastAPI format (predicted_gender)
        String genderStr = null;

        // Try new FastAPI format first
        if (result.has("predicted_gender")) {
            genderStr = result.get("predicted_gender").asText();
        }
        // Fallback to old format
        else if (result.has("gender")) {
            genderStr = result.get("gender").asText();
        }

        if (genderStr != null) {
            genderStr = genderStr.toUpperCase();
            log.debug("Parsing gender from result: {}", genderStr);

            try {
                // Handle variations in gender detection output
                switch (genderStr) {
                    case "MALE":
                    case "M":
                    case "MAN":
                        return Gender.MALE;
                    case "FEMALE":
                    case "F":
                    case "WOMAN":
                        return Gender.FEMALE;
                    default:
                        log.warn("Unknown gender value: {}", genderStr);
                        return Gender.NOT_SPECIFIED;
                }
            } catch (Exception e) {
                log.warn("Could not parse gender from result: {}", genderStr);
                return Gender.NOT_SPECIFIED;
            }
        }

        log.warn("No gender field found in result: {}", result);
        return Gender.NOT_SPECIFIED;
    }

    @Transactional
    public void skipGenderDetection(UUID riderId) {
        log.info("Skipping gender detection for rider: {}", riderId);

        Rider rider = riderRepository.findByIdWithUser(riderId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Rider not found"));

        rider.setGender(Gender.NOT_SPECIFIED);
        rider.setGenderVerified(false);
        rider.setWomenOnlyAccess(false);

        riderRepository.save(rider);
        log.info("Gender detection skipped for rider: {}", riderId);
    }
}