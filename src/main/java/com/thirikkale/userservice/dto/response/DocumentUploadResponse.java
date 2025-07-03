package com.thirikkale.userservice.dto.response;

import com.thirikkale.userservice.model.enums.DocumentType;
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

    // Face verification specific fields
    private Double faceMatchScore;
    private String faceVerificationStatus;

    // OCR extraction specific fields
    private String extractedFirstName;
    private String extractedLastName;
    private String extractedLicenseNumber;
    private String profileExtractionStatus;

    public static DocumentUploadResponse success(UUID driverId, DocumentType documentType,
                                                 String fileUrl, String message) {
        return DocumentUploadResponse.builder()
                .driverId(driverId)
                .documentType(documentType)
                .fileUrl(fileUrl)
                .uploaded(true)
                .message(message)
                .build();
    }

    public static DocumentUploadResponse failure(UUID driverId, DocumentType documentType,
                                                 String message) {
        return DocumentUploadResponse.builder()
                .driverId(driverId)
                .documentType(documentType)
                .uploaded(false)
                .message(message)
                .build();
    }
}