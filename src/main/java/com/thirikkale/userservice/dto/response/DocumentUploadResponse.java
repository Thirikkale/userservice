package com.thirikkale.userservice.dto.response;

import com.thirikkale.userservice.model.enums.DocumentType;
import com.thirikkale.userservice.model.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {

    private UUID driverId;
    private DocumentType documentType;
    private String fileUrl;
    private boolean uploaded;
    private String message;
    private int verificationProgress; // 0-100%
    private String nextStep;

    // Face verification specific fields - ENHANCED
    private Double faceMatchScore;
    private String faceVerificationStatus; // PENDING, IN_PROGRESS, VERIFIED, FAILED, MANUAL_REVIEW
    private Integer faceVerificationAttempts;

    // OCR extraction specific fields - ENHANCED
    private String extractedFirstName;
    private String extractedLastName;
    private String extractedLicenseNumber;
    private String profileExtractionStatus; // PENDING, IN_PROGRESS, COMPLETED, FAILED

    // NEW: Raw extracted text from OCR
    private String extractedText;

    private VehicleType vehicleType;
    private String vehicleModel; //future
    private String vehicleColor; //future

//New helper method
    public static DocumentUploadResponse vehicleTypeSuccess(UUID driverId, VehicleType vehicleType, String message) {
        return DocumentUploadResponse.builder()
                .driverId(driverId)
                .documentType(DocumentType.VEHICLE_TYPE_DECLARATION)
                .uploaded(true)
                .message(message)
                .vehicleType(vehicleType)
                .verificationProgress(10) // Small progress for vehicle type
                .nextStep("Continue uploading required documents")
                .build();
    }

    // Document verification fields
    private String documentVerificationStatus; // PENDING, IN_PROGRESS, VERIFIED, REJECTED

    public static DocumentUploadResponse success(UUID driverId, DocumentType documentType,
                                                 String fileUrl, String message) {
        return DocumentUploadResponse.builder()
                .driverId(driverId)
                .documentType(documentType)
                .fileUrl(fileUrl)
                .uploaded(true)
                .message(message)
                .verificationProgress(0)
                .nextStep("Continue uploading documents")
                .build();
    }

    public static DocumentUploadResponse failure(UUID driverId, DocumentType documentType,
                                                 String message) {
        return DocumentUploadResponse.builder()
                .driverId(driverId)
                .documentType(documentType)
                .uploaded(false)
                .message(message)
                .verificationProgress(0)
                .nextStep("Please retry with a valid file")
                .build();
    }

    // Helper method to check if face verification is complete
    public boolean isFaceVerificationComplete() {
        return "VERIFIED".equals(faceVerificationStatus) ||
                "FAILED".equals(faceVerificationStatus) ||
                "MANUAL_REVIEW".equals(faceVerificationStatus);
    }

    // Helper method to check if OCR extraction is complete
    public boolean isOcrExtractionComplete() {
        return "COMPLETED".equals(profileExtractionStatus) ||
                "FAILED".equals(profileExtractionStatus);
    }
}