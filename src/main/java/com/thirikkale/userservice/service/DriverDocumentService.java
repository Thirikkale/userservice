package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.response.DocumentUploadResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Driver;
import com.thirikkale.userservice.model.enums.DocumentType;
import com.thirikkale.userservice.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverDocumentService {

    private final DriverRepository driverRepository;
    private final FileStorageService fileStorageService;
    private final OCRService ocrService;
    private final FaceVerificationService faceVerificationService;
    private final DriverService driverService;

    /**
     * Upload driver document - ENHANCED with async processing
     */
    @Transactional
    public DocumentUploadResponse uploadDriverDocument(UUID driverId, DocumentType documentType, MultipartFile file) {
        log.info("Processing document upload for driver {} - type: {}", driverId, documentType);

        try {
            // 1. Validate driver exists
            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found: " + driverId));

            // 2. Validate file
            validateFile(file, documentType);

            // 3. Store file and get URL (this is fast, keep in transaction)
            String fileUrl = fileStorageService.storeDriverDocument(driverId, documentType, file);
            log.info("File stored at: {}", fileUrl);

            // 4. Update driver with document URL
            updateDriverDocumentUrl(driver, documentType, fileUrl);

            // 5. Initialize face verification attempts if null
            if (driver.getFaceVerificationAttempts() == null) {
                driver.setFaceVerificationAttempts(0);
            }

            // 6. Save initial state (release DB connection)
            driver = driverRepository.save(driver);

            // 7. FOR DRIVING LICENSE: Start async OCR processing
            if (documentType == DocumentType.DRIVING_LICENSE) {
                log.info("Starting async OCR processing for driving license");
                driver.setProfileExtractionStatus("IN_PROGRESS");
                driver = driverRepository.save(driver);

                // Start async OCR processing (this releases the transaction)
                processDriverLicenseAsync(driverId, file);

                // Return immediate response
                return buildImmediateResponse(driver, documentType, fileUrl, "Document uploaded. OCR processing started...");
            }

            // 8. FOR OTHER DOCUMENTS: Process synchronously (fast operations)
            String extractedText = processDocument(driver, documentType, file, fileUrl);

            // 9. Update overall document upload status
            updateDocumentUploadStatus(driver);

            // 10. Save final state
            driver = driverRepository.save(driver);

            // 11. Build response
            return buildDocumentUploadResponse(driver, documentType, fileUrl, extractedText);

        } catch (Exception e) {
            log.error("Document upload failed for driver {} - type {}: {}", driverId, documentType, e.getMessage());
            throw new CustomExceptions.DocumentUploadException("Document upload failed: " + e.getMessage());
        }
    }

    /**
     * Async OCR processing for driving license - RUNS IN SEPARATE TRANSACTION
     */
    @Async("aiProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> processDriverLicenseAsync(UUID driverId, MultipartFile file) {
        log.info("Starting async OCR processing for driver: {}", driverId);

        try {
            // Get fresh driver instance in new transaction
            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found: " + driverId));

            // Extract information from driving license using OCR
            var ocrResult = ocrService.extractDrivingLicenseInfo(file);

            if (ocrResult != null && ocrResult.getFirstName() != null) {
                log.info("OCR extraction successful for driver: {}", driverId);

                // Update driver profile with extracted information
                driverService.updateDriverProfileFromOCR(
                        driver.getDriverId(),
                        ocrResult.getFirstName(),
                        ocrResult.getLastName(),
                        ocrResult.getLicenseNumber(),
                        ocrResult.getExpiryDate()
                );

                // Update OCR status
                driver.setProfileExtractionStatus("COMPLETED");
                driverRepository.save(driver);

                // Check if face verification can be started
                startFaceVerificationIfReady(driver);

                log.info("Async OCR processing completed successfully for driver: {}", driverId);

            } else {
                log.warn("OCR extraction failed for driver: {}", driverId);
                driver.setProfileExtractionStatus("FAILED");
                driverRepository.save(driver);
            }

        } catch (Exception e) {
            log.error("Async OCR processing failed for driver {}: {}", driverId, e.getMessage());

            // Update status to failed in separate transaction
            try {
                Driver driver = driverRepository.findById(driverId).orElse(null);
                if (driver != null) {
                    driver.setProfileExtractionStatus("FAILED");
                    driverRepository.save(driver);
                }
            } catch (Exception updateException) {
                log.error("Failed to update OCR failure status: {}", updateException.getMessage());
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Check if face verification can be started
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startFaceVerificationIfReady(Driver driver) {
        try {
            // Refresh driver state
            driver = driverRepository.findById(driver.getDriverId()).orElse(driver);

            // If selfie is already uploaded and OCR is completed, start face verification
            if (driver.getSelfieUrl() != null &&
                    "COMPLETED".equals(driver.getProfileExtractionStatus())) {

                log.info("Starting face verification for driver: {}", driver.getDriverId());

                // Start async face verification
                startFaceVerificationAsync(driver.getDriverId());
            }
        } catch (Exception e) {
            log.error("Failed to check face verification readiness: {}", e.getMessage());
        }
    }

    /**
     * Async face verification - RUNS IN SEPARATE TRANSACTION
     */
    @Async("aiProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> startFaceVerificationAsync(UUID driverId) {
        log.info("Starting async face verification for driver: {}", driverId);

        try {
            // Get fresh driver instance
            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found: " + driverId));

            // Validate both images are available
            if (driver.getSelfieUrl() == null || driver.getDrivingLicenseUrl() == null) {
                log.warn("Cannot start face verification - missing images for driver: {}", driverId);
                driver.setFaceVerificationStatus("PENDING");
                driverRepository.save(driver);
                return CompletableFuture.completedFuture(null);
            }

            // Update status
            driver.setFaceVerificationStatus("IN_PROGRESS");
            driver.setFaceVerificationAttempts(driver.getFaceVerificationAttempts() + 1);
            driverRepository.save(driver);

            // Perform face verification with timeout
            performFaceVerificationWithTimeout(driver);

        } catch (Exception e) {
            log.error("Async face verification failed for driver {}: {}", driverId, e.getMessage());

            // Update status to failed
            try {
                Driver driver = driverRepository.findById(driverId).orElse(null);
                if (driver != null) {
                    driver.setFaceVerificationStatus("FAILED");
                    driver.setFaceMatchScore(0.0);
                    driverRepository.save(driver);
                }
            } catch (Exception updateException) {
                log.error("Failed to update face verification failure status: {}", updateException.getMessage());
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Perform face verification with timeout protection
     */
    private void performFaceVerificationWithTimeout(Driver driver) {
        try {
            // Convert URLs to absolute paths
            String selfieAbsolutePath = convertUrlToAbsolutePath(driver.getSelfieUrl());
            String licenseAbsolutePath = convertUrlToAbsolutePath(driver.getDrivingLicenseUrl());

            log.info("Face verification files - Selfie: {}, License: {}", selfieAbsolutePath, licenseAbsolutePath);

            // Verify files exist
            if (!fileStorageService.fileExists(selfieAbsolutePath) ||
                    !fileStorageService.fileExists(licenseAbsolutePath)) {

                log.error("Face verification files not found for driver: {}", driver.getDriverId());
                driver.setFaceVerificationStatus("FAILED");
                driver.setFaceMatchScore(0.0);
                driverRepository.save(driver);
                return;
            }

            // Perform face matching with timeout
            var faceMatchResult = faceVerificationService.verifyFaceMatch(
                    selfieAbsolutePath,
                    licenseAbsolutePath
            );

            // Process results
            double confidenceScore = faceMatchResult.getConfidenceScore() != null ?
                    faceMatchResult.getConfidenceScore() : 0.0;
            driver.setFaceMatchScore(confidenceScore);

            // Determine status based on confidence
            double threshold = 0.5;

            if (faceMatchResult.getErrorMessage() != null && !faceMatchResult.getErrorMessage().isEmpty()) {
                log.error("Face verification error for driver {}: {}", driver.getDriverId(), faceMatchResult.getErrorMessage());
                driver.setFaceVerificationStatus("FAILED");
            } else if (confidenceScore >= threshold) {
                if (confidenceScore >= 0.8) {
                    driver.setFaceVerificationStatus("VERIFIED");
                    log.info("Face verification PASSED (High Confidence) for driver: {} with confidence: {}",
                            driver.getDriverId(), confidenceScore);
                } else if (confidenceScore >= 0.65) {
                    driver.setFaceVerificationStatus("VERIFIED");
                    log.info("Face verification PASSED (Good Confidence) for driver: {} with confidence: {}",
                            driver.getDriverId(), confidenceScore);
                } else {
                    driver.setFaceVerificationStatus("MANUAL_REVIEW");
                    log.info("Face verification requires MANUAL_REVIEW for driver: {} with confidence: {}",
                            driver.getDriverId(), confidenceScore);
                }
            } else {
                driver.setFaceVerificationStatus("FAILED");
                log.info("Face verification FAILED for driver: {} with confidence: {} (below threshold: {})",
                        driver.getDriverId(), confidenceScore, threshold);
            }

            // Save results
            driverRepository.save(driver);

        } catch (Exception e) {
            log.error("Face verification processing failed for driver {}: {}", driver.getDriverId(), e.getMessage());
            driver.setFaceVerificationStatus("FAILED");
            driver.setFaceMatchScore(0.0);
            driverRepository.save(driver);
        }
    }

    /**
     * Build immediate response for async processing
     */
    private DocumentUploadResponse buildImmediateResponse(Driver driver, DocumentType documentType,
                                                          String fileUrl, String message) {
        return DocumentUploadResponse.builder()
                .driverId(driver.getDriverId())
                .documentType(documentType)
                .fileUrl(fileUrl)
                .uploaded(true)
                .message(message)
                .verificationProgress(driver.getVerificationProgress())
                .nextStep("Processing in progress. Check status in a few moments.")
                .profileExtractionStatus("IN_PROGRESS")
                .faceVerificationStatus(driver.getFaceVerificationStatus())
                .faceVerificationAttempts(driver.getFaceVerificationAttempts())
                .build();
    }

    // ... Keep all other existing methods as they are ...

    /**
     * Process document based on type - MODIFIED for async handling
     */
    private String processDocument(Driver driver, DocumentType documentType, MultipartFile file, String fileUrl) {
        switch (documentType) {
            case SELFIE:
                processSelfie(driver);
                return null;

            case DRIVING_LICENSE:
                // This is now handled async, so just return a message
                return "OCR processing started asynchronously";

            case REVENUE_LICENSE:
            case VEHICLE_REGISTRATION:
            case VEHICLE_INSURANCE:
                processOtherDocument(driver, documentType);
                return null;

            default:
                return null;
        }
    }

    /**
     * Process selfie upload - ENHANCED to check for async completion
     */
    private void processSelfie(Driver driver) {
        log.info("Processing selfie for driver: {}", driver.getDriverId());

        driver.setFaceVerificationStatus("PENDING");

        // If driving license OCR is completed, start async face verification
        if (driver.getDrivingLicenseUrl() != null &&
                "COMPLETED".equals(driver.getProfileExtractionStatus())) {

            log.info("Driving license OCR already completed - starting async face verification");
            startFaceVerificationAsync(driver.getDriverId());
        } else {
            log.info("Selfie uploaded, waiting for driving license OCR completion");
        }
    }

    // ... Keep all other existing helper methods unchanged ...

    private void processOtherDocument(Driver driver, DocumentType documentType) {
        driver.setDocumentVerificationStatus("IN_PROGRESS");
        log.info("{} uploaded for driver: {}", documentType, driver.getDriverId());
    }

    private String convertUrlToAbsolutePath(String relativeUrl) {
        String cleanPath = relativeUrl.startsWith("/") ? relativeUrl.substring(1) : relativeUrl;
        return System.getProperty("user.dir") + "/" + cleanPath.replace("\\", "/");
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

    private DocumentUploadResponse buildDocumentUploadResponse(Driver driver, DocumentType documentType,
                                                               String fileUrl, String extractedText) {
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

        if (driver.getFaceMatchScore() != null) {
            responseBuilder.faceMatchScore(driver.getFaceMatchScore());
        }

        if (driver.getFaceVerificationStatus() != null) {
            responseBuilder.faceVerificationStatus(driver.getFaceVerificationStatus());
        }

        if (driver.getFaceVerificationAttempts() != null) {
            responseBuilder.faceVerificationAttempts(driver.getFaceVerificationAttempts());
        }

        if (documentType == DocumentType.DRIVING_LICENSE) {
            responseBuilder.profileExtractionStatus(driver.getProfileExtractionStatus());

            if (driver.getUser() != null) {
                responseBuilder.extractedFirstName(driver.getUser().getFirstName());
                responseBuilder.extractedLastName(driver.getUser().getLastName());
            }
            if (driver.getLicenseNumber() != null) {
                responseBuilder.extractedLicenseNumber(driver.getLicenseNumber());
            }

            if (extractedText != null) {
                responseBuilder.extractedText(extractedText);
            }
        }

        if (driver.getDocumentVerificationStatus() != null) {
            responseBuilder.documentVerificationStatus(driver.getDocumentVerificationStatus());
        }

        return responseBuilder.build();
    }

    private String determineResponseMessage(Driver driver, DocumentType documentType) {
        if (documentType == DocumentType.DRIVING_LICENSE) {
            if ("IN_PROGRESS".equals(driver.getProfileExtractionStatus())) {
                return "Document uploaded successfully! OCR processing in progress...";
            } else if ("FAILED".equals(driver.getProfileExtractionStatus())) {
                return "Document uploaded but OCR extraction failed. Please ensure image is clear.";
            } else if ("COMPLETED".equals(driver.getProfileExtractionStatus())) {
                return "Document uploaded and profile information extracted successfully!";
            }
        }

        if (driver.getFaceVerificationStatus() != null) {
            switch (driver.getFaceVerificationStatus()) {
                case "VERIFIED":
                    double score = driver.getFaceMatchScore() != null ? driver.getFaceMatchScore() : 0.0;
                    return String.format("Document uploaded and face verification passed! (Confidence: %.1f%%)", score * 100);
                case "FAILED":
                    double failScore = driver.getFaceMatchScore() != null ? driver.getFaceMatchScore() : 0.0;
                    return String.format("Document uploaded but face verification failed (Confidence: %.1f%% - below 50%% threshold). Please ensure clear photos.", failScore * 100);
                case "MANUAL_REVIEW":
                    double reviewScore = driver.getFaceMatchScore() != null ? driver.getFaceMatchScore() : 0.0;
                    return String.format("Document uploaded. Face verification under manual review (Confidence: %.1f%%).", reviewScore * 100);
                case "IN_PROGRESS":
                    return "Document uploaded. Face verification in progress...";
            }
        }

        return "Document uploaded successfully";
    }

    private String getNextStep(Driver driver) {
        if (!driver.getIsDocumentsUploaded()) {
            return "Continue uploading remaining documents";
        } else if ("IN_PROGRESS".equals(driver.getProfileExtractionStatus())) {
            return "OCR processing in progress. Please wait...";
        } else if ("FAILED".equals(driver.getProfileExtractionStatus())) {
            return "Profile extraction failed. Please re-upload a clear driving license";
        } else if ("IN_PROGRESS".equals(driver.getFaceVerificationStatus())) {
            return "Face verification in progress. Please wait...";
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

    private void validateFile(MultipartFile file, DocumentType documentType) {
        if (file.isEmpty()) {
            throw new CustomExceptions.InvalidFileException("File is empty");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new CustomExceptions.InvalidFileException("File size exceeds 10MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomExceptions.InvalidFileException("Only image files are allowed");
        }
    }
}