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
@Transactional
public class DriverDocumentService {

    private final DriverRepository driverRepository;
    private final FileStorageService fileStorageService;
    private final OCRService ocrService;
    private final FaceVerificationService faceVerificationService;
    private final DriverService driverService;

    /**
     * Upload driver document and trigger appropriate processing
     */
    public DocumentUploadResponse uploadDriverDocument(UUID driverId, DocumentType documentType, MultipartFile file) {
        log.info("Processing document upload for driver {} - type: {}", driverId, documentType);

        try {
            // 1. Validate driver exists
            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found: " + driverId));

            // 2. Validate file
            validateFile(file, documentType);

            // 3. Store file and get URL
            String fileUrl = fileStorageService.storeDriverDocument(driverId, documentType, file);

            // 4. Update driver with document URL
            updateDriverDocumentUrl(driver, documentType, fileUrl);

            // 5. Process the document based on type
            processDocument(driver, documentType, file, fileUrl);

            // 6. Update overall document upload status
            updateDocumentUploadStatus(driver);

            // 7. Save driver changes
            driver = driverRepository.save(driver);

            // 8. Build comprehensive response
            return buildDocumentUploadResponse(driver, documentType, fileUrl);

        } catch (Exception e) {
            log.error("Document upload failed for driver {} - type {}: {}", driverId, documentType, e.getMessage());
            throw new CustomExceptions.DocumentUploadException("Document upload failed: " + e.getMessage());
        }
    }

    /**
     * Process document based on type - ENHANCED with better face verification integration
     */
    private void processDocument(Driver driver, DocumentType documentType, MultipartFile file, String fileUrl) {
        switch (documentType) {
            case SELFIE:
                processSelfie(driver);
                break;

            case DRIVING_LICENSE:
                processDrivingLicense(driver, file, fileUrl);
                break;

            case REVENUE_LICENSE:
            case VEHICLE_REGISTRATION:
            case VEHICLE_INSURANCE:
                processOtherDocument(driver, documentType);
                break;
        }
    }

    /**
     * Process selfie upload - check if driving license is ready for face verification
     */
    private void processSelfie(Driver driver) {
        log.info("Processing selfie for driver: {}", driver.getDriverId());

        // If driving license is already uploaded, start face verification
        if (driver.getDrivingLicenseUrl() != null &&
                "COMPLETED".equals(driver.getProfileExtractionStatus())) {

            log.info("Both selfie and driving license available - starting face verification");
            startFaceVerification(driver);
        } else {
            log.info("Selfie uploaded, waiting for driving license to complete face verification");
            driver.setFaceVerificationStatus("PENDING");
        }
    }

    /**
     * Process driving license - extract info and start face verification if selfie available
     */
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

            // Mark OCR as completed
            driver.setProfileExtractionStatus("COMPLETED");

            // If selfie is already uploaded, start face verification
            if (driver.getSelfieUrl() != null) {
                log.info("Both driving license and selfie available - starting face verification");
                startFaceVerification(driver);
            } else {
                log.info("Driving license processed, waiting for selfie to complete face verification");
                driver.setFaceVerificationStatus("PENDING");
            }

        } catch (Exception e) {
            log.error("OCR processing failed for driver {}: {}", driver.getDriverId(), e.getMessage());
            driver.setProfileExtractionStatus("FAILED");
            driver.setFaceVerificationStatus("PENDING"); // Can retry after fixing OCR
        }
    }

    /**
     * Process other documents
     */
    private void processOtherDocument(Driver driver, DocumentType documentType) {
        driver.setDocumentVerificationStatus("IN_PROGRESS");
        log.info("{} uploaded for driver: {}", documentType, driver.getDriverId());
    }

    /**
     * Start face verification between selfie and driving license - ENHANCED
     */
    private void startFaceVerification(Driver driver) {
        try {
            log.info("Starting face verification for driver: {}", driver.getDriverId());

            // Validate both images are available
            if (driver.getSelfieUrl() == null || driver.getDrivingLicenseUrl() == null) {
                log.warn("Cannot start face verification - missing images for driver: {}", driver.getDriverId());
                driver.setFaceVerificationStatus("PENDING");
                return;
            }

            driver.setFaceVerificationStatus("IN_PROGRESS");
            driver.setFaceVerificationAttempts(driver.getFaceVerificationAttempts() + 1);

            // Perform face matching between selfie and driving license photo
            var faceMatchResult = faceVerificationService.verifyFaceMatch(
                    driver.getSelfieUrl(),
                    driver.getDrivingLicenseUrl()
            );

            // Store the match score
            driver.setFaceMatchScore(faceMatchResult.getConfidenceScore());

            // Determine verification status based on confidence score
            double confidenceScore = faceMatchResult.getConfidenceScore();

            log.info("Face verification result for driver {}: match={}, confidence={}",
                    driver.getDriverId(), faceMatchResult.isMatch(), confidenceScore);

            if (faceMatchResult.isMatch() && confidenceScore >= 0.8) {
                // High confidence match
                driver.setFaceVerificationStatus("VERIFIED");
                log.info("Face verification PASSED for driver: {} with confidence: {}",
                        driver.getDriverId(), confidenceScore);

            } else if (confidenceScore >= 0.6) {
                // Borderline case - send for manual review
                driver.setFaceVerificationStatus("MANUAL_REVIEW");
                log.info("Face verification requires MANUAL_REVIEW for driver: {} with confidence: {}",
                        driver.getDriverId(), confidenceScore);

            } else {
                // Low confidence - failed
                driver.setFaceVerificationStatus("FAILED");
                log.info("Face verification FAILED for driver: {} with confidence: {}",
                        driver.getDriverId(), confidenceScore);
            }

        } catch (Exception e) {
            log.error("Face verification failed for driver {}: {}", driver.getDriverId(), e.getMessage());
            driver.setFaceVerificationStatus("FAILED");
            driver.setFaceMatchScore(0.0);
        }
    }

    /**
     * Update driver document URL based on type
     */
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

    /**
     * Update overall document upload status
     */
    private void updateDocumentUploadStatus(Driver driver) {
        boolean allUploaded = driver.getSelfieUrl() != null &&
                driver.getDrivingLicenseUrl() != null &&
                driver.getRevenueLicenseUrl() != null &&
                driver.getVehicleRegistrationUrl() != null &&
                driver.getVehicleInsuranceUrl() != null;

        driver.setIsDocumentsUploaded(allUploaded);

        if (allUploaded) {
            log.info("All documents uploaded for driver: {}", driver.getDriverId());
        }
    }

    /**
     * Build comprehensive response including face verification status - ENHANCED
     */
    private DocumentUploadResponse buildDocumentUploadResponse(Driver driver, DocumentType documentType, String fileUrl) {
        String message = determineResponseMessage(driver, documentType);
        String nextStep = getNextStep(driver);
        int progress = driver.getVerificationProgress();

        DocumentUploadResponse.DocumentUploadResponseBuilder responseBuilder = DocumentUploadResponse.builder()
                .driverId(driver.getDriverId())
                .documentType(documentType)
                .fileUrl(fileUrl)
                .uploaded(true)
                .message(message)
                .verificationProgress(progress)
                .nextStep(nextStep);

        // Add face verification details if available
        if (driver.getFaceMatchScore() != null) {
            responseBuilder.faceMatchScore(driver.getFaceMatchScore());
        }

        if (driver.getFaceVerificationStatus() != null) {
            responseBuilder.faceVerificationStatus(driver.getFaceVerificationStatus());
        }

        // Add OCR extraction details if available
        if (documentType == DocumentType.DRIVING_LICENSE) {
            responseBuilder.profileExtractionStatus(driver.getProfileExtractionStatus());

            // Add extracted information if available
            if (driver.getUser() != null) {
                responseBuilder.extractedFirstName(driver.getUser().getFirstName());
                responseBuilder.extractedLastName(driver.getUser().getLastName());
            }
            if (driver.getLicenseNumber() != null) {
                responseBuilder.extractedLicenseNumber(driver.getLicenseNumber());
            }
        }

        return responseBuilder.build();
    }

    /**
     * Determine appropriate response message
     */
    private String determineResponseMessage(Driver driver, DocumentType documentType) {
        if (documentType == DocumentType.DRIVING_LICENSE && "FAILED".equals(driver.getProfileExtractionStatus())) {
            return "Document uploaded but OCR extraction failed. Please ensure image is clear.";
        }

        if (driver.getFaceVerificationStatus() != null) {
            switch (driver.getFaceVerificationStatus()) {
                case "VERIFIED":
                    return "Document uploaded and face verification passed!";
                case "FAILED":
                    return "Document uploaded but face verification failed. Please ensure clear photos.";
                case "MANUAL_REVIEW":
                    return "Document uploaded. Face verification under manual review.";
                case "IN_PROGRESS":
                    return "Document uploaded. Face verification in progress...";
            }
        }

        return "Document uploaded successfully";
    }

    /**
     * Get next step for driver
     */
    private String getNextStep(Driver driver) {
        if (!driver.getIsDocumentsUploaded()) {
            return "Continue uploading remaining documents";
        } else if ("PENDING".equals(driver.getProfileExtractionStatus())) {
            return "Waiting for profile information extraction from driving license";
        } else if ("FAILED".equals(driver.getProfileExtractionStatus())) {
            return "Profile extraction failed. Please re-upload a clear driving license";
        } else if ("IN_PROGRESS".equals(driver.getFaceVerificationStatus())) {
            return "Face verification in progress";
        } else if ("MANUAL_REVIEW".equals(driver.getFaceVerificationStatus())) {
            return "Face verification requires manual review by support team";
        } else if ("FAILED".equals(driver.getFaceVerificationStatus())) {
            return "Face verification failed. Please re-upload clear selfie and driving license";
        } else if ("VERIFIED".equals(driver.getFaceVerificationStatus()) && driver.isFullyVerified()) {
            return "Verification complete! You can now go online and start accepting rides";
        } else {
            return "Document verification in progress";
        }
    }

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file, DocumentType documentType) {
        if (file.isEmpty()) {
            throw new CustomExceptions.InvalidFileException("File is empty");
        }

        // Check file size (10MB limit)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new CustomExceptions.InvalidFileException("File size exceeds 10MB limit");
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomExceptions.InvalidFileException("Only image files are allowed");
        }
    }
}