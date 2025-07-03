package com.thirikkale.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrExtractionResponse {

    private UUID driverId;
    private Boolean success;
    private String extractionStatus; // COMPLETED, FAILED, IN_PROGRESS
    private String message;

    // Extracted information from driving license
    private String extractedFirstName;
    private String extractedLastName;
    private String extractedLicenseNumber;
    private String extractedExpiryDate;
    private String extractedDateOfBirth;
    private String extractedAddress;

    // Raw OCR data
    private String rawOcrText;
    private Double extractionConfidence;
    private String ocrMethod;

    // Validation status
    private Boolean nameMatches;
    private Boolean licenseNumberValid;
    private Boolean notExpired;

    public static OcrExtractionResponse success(UUID driverId, String firstName, String lastName,
            String licenseNumber, String expiryDate, String dateOfBirth,
            String rawText, Double confidence) {
        return OcrExtractionResponse.builder()
                .driverId(driverId)
                .success(true)
                .extractionStatus("COMPLETED")
                .message("OCR extraction completed successfully")
                .extractedFirstName(firstName)
                .extractedLastName(lastName)
                .extractedLicenseNumber(licenseNumber)
                .extractedExpiryDate(expiryDate)
                .extractedDateOfBirth(dateOfBirth)
                .rawOcrText(rawText)
                .extractionConfidence(confidence)
                .ocrMethod("EASYOCR_AI")
                .build();
    }

    public static OcrExtractionResponse failure(UUID driverId, String message, String rawText) {
        return OcrExtractionResponse.builder()
                .driverId(driverId)
                .success(false)
                .extractionStatus("FAILED")
                .message(message)
                .rawOcrText(rawText)
                .extractionConfidence(0.0)
                .ocrMethod("EASYOCR_AI")
                .build();
    }
}
