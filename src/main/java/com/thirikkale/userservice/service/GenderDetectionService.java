package com.thirikkale.userservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.thirikkale.userservice.dto.response.GenderDetectionResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Rider;
import com.thirikkale.userservice.model.enums.Gender;
import com.thirikkale.userservice.repository.RiderRepository;
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
    private final FileStorageService fileStorageService;
    private final PythonIntegrationService pythonIntegrationService;

    @Transactional
    public GenderDetectionResponse detectGenderFromSelfie(UUID riderId, MultipartFile selfieFile) {
        log.info("Detecting gender for rider: {}", riderId);

        // Find rider
        Rider rider = riderRepository.findByIdWithUser(riderId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Rider not found"));

        try {
            // Store selfie file
            String selfieUrl = fileStorageService.storeFile(selfieFile, "selfies", riderId.toString());

            // Save the selfie to a temporary file for Python processing
            Path tempSelfie = fileStorageService.saveToTempFile(selfieFile, "selfie_" + riderId.toString());

            // Call Python script for gender detection
            JsonNode result = pythonIntegrationService.executePythonScript("gender.py", tempSelfie.toString());

            // Parse the result
            Gender detectedGender = parseGenderFromResult(result);
            double confidence = result.has("confidence") ? result.get("confidence").asDouble() : 0.0;

            // Update rider information
            rider.setSelfieUrl(selfieUrl);
            rider.setGender(detectedGender);
            rider.setGenderVerified(true);

            // Grant women-only access if female detected with high confidence
            boolean womenOnlyAccess = (detectedGender == Gender.FEMALE && confidence > 0.8);
            rider.setWomenOnlyAccess(womenOnlyAccess);

            riderRepository.save(rider);

            // Clean up temporary file
            fileStorageService.deleteTempFile(tempSelfie);

            log.info("Gender detection completed for rider: {} - Gender: {} - Confidence: {} - Women Only Access: {}",
                    riderId, detectedGender, confidence, womenOnlyAccess);

            return GenderDetectionResponse.builder()
                    .riderId(riderId)
                    .detectedGender(detectedGender)
                    .confidence(confidence)
                    .womenOnlyAccessGranted(womenOnlyAccess)
                    .message(womenOnlyAccess ? "Gender verified as female. Women-only rides feature enabled."
                            : String.format("Gender detected as %s with %.2f%% confidence.",
                                    detectedGender.toString().toLowerCase(), confidence * 100))
                    .build();

        } catch (Exception e) {
            log.error("Failed to detect gender for rider {}: {}", riderId, e.getMessage());

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
        if (result.has("gender")) {
            String genderStr = result.get("gender").asText().toUpperCase();
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
                        return Gender.NOT_SPECIFIED;
                }
            } catch (Exception e) {
                log.warn("Could not parse gender from result: {}", genderStr);
                return Gender.NOT_SPECIFIED;
            }
        }
        return Gender.NOT_SPECIFIED;
    }

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