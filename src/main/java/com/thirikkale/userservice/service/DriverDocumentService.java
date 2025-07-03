package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.response.DocumentUploadResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Driver;
import com.thirikkale.userservice.model.enums.DocumentType;
import com.thirikkale.userservice.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverDocumentService {

    private final DriverRepository driverRepository;
    private final FileStorageService fileStorageService;
    private final OCRService ocrService;
    private final FaceVerificationService faceVerificationService;
    private final DriverService driverService;

    @Transactional
    public DocumentUploadResponse uploadDriverDocument(UUID driverId, DocumentType documentType,
                                                       MultipartFile file) {
        log.info("Uploading {} for driver: {}", documentType, driverId);

        Driver driver = driverRepository.findByIdWithUser(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        // Validate file
        validateDocumentFile(file, documentType);

        try {
            // Store file
            String fileUrl = fileStorageService.storeFile(file, "driver-documents",
                    driverId + "/" + documentType.name().toLowerCase());

            // Update driver with document URL
            updateDriverDocumentUrl(driver, documentType, fileUrl);

            // Process document based on type
            processDocument(driver, documentType, file, fileUrl);

            // Check if all documents are uploaded
            updateDocumentUploadStatus(driver);

            driverRepository.save(driver);

            log.info("{} uploaded successfully for driver: {}", documentType, driverId);

            return DocumentUploadResponse.builder()
                    .driverId(driverId)
                    .documentType(documentType)
                    .fileUrl(fileUrl)
                    .uploaded(true)
                    .message("Document uploaded successfully")
                    .verificationProgress(driver.getVerificationProgress())
                    .nextStep(getNextStep(driver))
                    .build();

        } catch (Exception e) {
            log.error("Failed to upload {} for driver {}: {}", documentType, driverId, e.getMessage());
            throw new CustomExceptions.DocumentUploadException("Failed to upload document: " + e.getMessage());
        }
    }

    private void validateDocumentFile(MultipartFile file, DocumentType documentType) {
        if (file.isEmpty()) {
            throw new CustomExceptions.InvalidFileException("File cannot be empty");
        }

        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new CustomExceptions.InvalidFileException("File size exceeds 10MB limit");
        }

        String contentType = file.getContentType();
        switch (documentType) {
            case SELFIE:
                if (!isImageFile(contentType)) {
                    throw new CustomExceptions.InvalidFileException("Selfie must be an image file");
                }
                break;
            case DRIVING_LICENSE:
            case REVENUE_LICENSE:
            case VEHICLE_REGISTRATION:
            case VEHICLE_INSURANCE:
                if (!isImageFile(contentType) && !isPdfFile(contentType)) {
                    throw new CustomExceptions.InvalidFileException("Document must be an image or PDF file");
                }
                break;
        }
    }

    private boolean isImageFile(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    private boolean isPdfFile(String contentType) {
        return "application/pdf".equals(contentType);
    }

    private void updateDriverDocumentUrl(Driver driver, DocumentType documentType, String fileUrl) {
        switch (documentType) {
            case SELFIE:
                driver.setSelfieUrl(fileUrl);
                break;
            case DRIVING_LICENSE:
                driver.setDrivingLicenseUrl(fileUrl);
                break;
            case REVENUE_LICENSE:
                driver.setRevenueLicenseUrl(fileUrl);
                break;
            case VEHICLE_REGISTRATION:
                driver.setVehicleRegistrationUrl(fileUrl);
                break;
            case VEHICLE_INSURANCE:
                driver.setVehicleInsuranceUrl(fileUrl);
                break;
        }
    }

    private void processDocument(Driver driver, DocumentType documentType, MultipartFile file, String fileUrl) {
        switch (documentType) {
            case SELFIE:
                // No immediate processing needed for selfie
                // Face verification will happen after driving license is processed
                log.info("Selfie uploaded for driver: {}", driver.getDriverId());
                break;

            case DRIVING_LICENSE:
                // Start OCR processing for driving license
                processDrivingLicense(driver, file, fileUrl);
                break;

            case REVENUE_LICENSE:
            case VEHICLE_REGISTRATION:
            case VEHICLE_INSURANCE:
                // Basic document validation - detailed processing can be added later
                driver.setDocumentVerificationStatus("IN_PROGRESS");
                log.info("{} uploaded for driver: {}", documentType, driver.getDriverId());
                break;
        }
    }

    private void processDrivingLicense(Driver driver, MultipartFile file, String fileUrl) {
        try {
            log.info("Starting OCR processing for driving license: {}", driver.getDriverId());

            // Update status to processing
            driver.setProfileExtractionStatus("IN_PROGRESS");

            // Extract information from driving license using OCR
            var ocrResult = ocrService.extractDrivingLicenseInfo(file);

            // Update driver profile with extracted information
            driverService.updateDriverProfileFromOCR(
                    driver.getDriverId(),
                    ocrResult.getFirstName(),
                    ocrResult.getLastName(),
                    ocrResult.getLicenseNumber(),
                    ocrResult.getExpiryDate()
            );

            // If selfie is already uploaded, start face verification
            if (driver.getSelfieUrl() != null) {
                startFaceVerification(driver);
            }

        } catch (Exception e) {
            log.error("OCR processing failed for driver {}: {}", driver.getDriverId(), e.getMessage());
            driver.setProfileExtractionStatus("FAILED");
        }
    }

    private void startFaceVerification(Driver driver) {
        try {
            log.info("Starting face verification for driver: {}", driver.getDriverId());

            driver.setFaceVerificationStatus("IN_PROGRESS");
            driver.setFaceVerificationAttempts(driver.getFaceVerificationAttempts() + 1);

            // Perform face matching between selfie and driving license photo
            var faceMatchResult = faceVerificationService.verifyFaceMatch(
                    driver.getSelfieUrl(),
                    driver.getDrivingLicenseUrl()
            );

            driver.setFaceMatchScore(faceMatchResult.getConfidenceScore());

            if (faceMatchResult.isMatch() && faceMatchResult.getConfidenceScore() > 0.8) {
                driver.setFaceVerificationStatus("VERIFIED");
                log.info("Face verification passed for driver: {}", driver.getDriverId());
            } else if (faceMatchResult.getConfidenceScore() > 0.6) {
                // Borderline case - send for manual review
                driver.setFaceVerificationStatus("MANUAL_REVIEW");
                log.info("Face verification requires manual review for driver: {}", driver.getDriverId());
            } else {
                driver.setFaceVerificationStatus("FAILED");
                log.info("Face verification failed for driver: {}", driver.getDriverId());
            }

        } catch (Exception e) {
            log.error("Face verification failed for driver {}: {}", driver.getDriverId(), e.getMessage());
            driver.setFaceVerificationStatus("FAILED");
        }
    }

    private void updateDocumentUploadStatus(Driver driver) {
        boolean allUploaded = driver.getSelfieUrl() != null &&
                driver.getDrivingLicenseUrl() != null &&
                driver.getRevenueLicenseUrl() != null &&
                driver.getVehicleRegistrationUrl() != null &&
                driver.getVehicleInsuranceUrl() != null;

        if (allUploaded && !driver.getIsDocumentsUploaded()) {
            driver.setIsDocumentsUploaded(true);
            driver.setDocumentVerificationStatus("IN_PROGRESS");
            log.info("All documents uploaded for driver: {}", driver.getDriverId());
        }
    }

    private String getNextStep(Driver driver) {
        if (!driver.getIsDocumentsUploaded()) {
            return "Continue uploading remaining documents";
        } else if ("PENDING".equals(driver.getProfileExtractionStatus())) {
            return "Waiting for profile information extraction from driving license";
        } else if ("IN_PROGRESS".equals(driver.getFaceVerificationStatus())) {
            return "Face verification in progress";
        } else if ("MANUAL_REVIEW".equals(driver.getFaceVerificationStatus())) {
            return "Face verification requires manual review by support team";
        } else if ("FAILED".equals(driver.getFaceVerificationStatus())) {
            return "Face verification failed. Please re-upload clear selfie and driving license";
        } else if (driver.isFullyVerified()) {
            return "Verification complete! You can now go online and start accepting rides";
        } else {
            return "Document verification in progress";
        }
    }
}