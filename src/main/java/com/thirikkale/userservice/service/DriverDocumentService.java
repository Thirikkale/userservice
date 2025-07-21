package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.response.DocumentUploadResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Driver;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.model.enums.DocumentType;
import com.thirikkale.userservice.repository.DriverRepository;
import com.thirikkale.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverDocumentService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final OCRService ocrService;
    private final FaceVerificationService faceVerificationService;
    private final DriverService driverService;

    /**
     * Upload driver document - ENHANCED with immediate transaction release
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

            // 6. FOR DRIVING LICENSE: Set status and save immediately
            if (documentType == DocumentType.DRIVING_LICENSE) {
                driver.setProfileExtractionStatus("IN_PROGRESS");
            }

            // 7. Process other documents synchronously (fast operations)
            if (documentType != DocumentType.DRIVING_LICENSE) {
                processDocument(driver, documentType, file, fileUrl);
                updateDocumentUploadStatus(driver);
            }

            // 8. Save state and RELEASE transaction immediately
            driver = driverRepository.save(driver);
            driverRepository.flush();

            // 9. Build immediate response BEFORE starting async processing
            DocumentUploadResponse response;
            if (documentType == DocumentType.DRIVING_LICENSE) {
                response = buildImmediateResponse(driver, documentType, fileUrl, "Document uploaded. OCR processing started...");
            } else {
                response = buildDocumentUploadResponse(driver, documentType, fileUrl, null);
            }

            // 10. Start async processing AFTER transaction is committed and response is built
            if (documentType == DocumentType.DRIVING_LICENSE) {
                // FIXED: Store file content before async processing
                startAsyncOcrProcessingDetached(driverId, fileUrl);
            }

            log.info("Document upload completed for driver {} - type: {}", driverId, documentType);
            return response;

        } catch (Exception e) {
            log.error("Document upload failed for driver {} - type {}: {}", driverId, documentType, e.getMessage());
            throw new CustomExceptions.DocumentUploadException("Document upload failed: " + e.getMessage());
        }
    }

    /**
     * Start async OCR processing - using file URL instead of MultipartFile
     */
    private void startAsyncOcrProcessingDetached(UUID driverId, String fileUrl) {
        // Create a new thread that's completely independent
        new Thread(() -> {
            try {
                // Small delay to ensure main transaction is fully committed
                Thread.sleep(200);
                processDriverLicenseAsync(driverId, fileUrl);
            } catch (Exception e) {
                log.error("Failed to start async OCR processing: {}", e.getMessage());
            }
        }, "OCR-Processing-" + driverId).start();
    }

    /**
     * Async OCR processing for driving license - FIXED to use file path
     */
    @Async("aiProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> processDriverLicenseAsync(UUID driverId, String fileUrl) {
        log.info("Starting async OCR processing for driver: {}", driverId);

        try {
            // Get fresh driver instance in new transaction
            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found: " + driverId));

            // FIXED: Convert URL to absolute file path and read as Path
            String absolutePath = convertUrlToAbsolutePath(fileUrl);
            Path filePath = Paths.get(absolutePath);

            if (!Files.exists(filePath)) {
                throw new IOException("File not found: " + absolutePath);
            }

            // Extract information from driving license using OCR with file path
            var ocrResult = ocrService.extractDrivingLicenseInfoFromPath(filePath);

            if (ocrResult != null && ocrResult.getFirstName() != null) {
                log.info("OCR extraction successful for driver: {}", driverId);

                try {
                    // Update driver profile with extracted information - with error handling
                    driverService.updateDriverProfileFromOCR(
                            driver.getDriverId(),
                            ocrResult.getFirstName(),
                            ocrResult.getLastName(),
                            ocrResult.getLicenseNumber(),
                            ocrResult.getExpiryDate()
                    );

                    // Update OCR status in a separate transaction
                    updateOcrStatus(driverId, "COMPLETED");

                    // Check if face verification can be started
                    startFaceVerificationIfReady(driver);

                    log.info("Async OCR processing completed successfully for driver: {}", driverId);

                } catch (Exception e) {
                    log.error("Failed to update driver profile from OCR for driver {}: {}", driverId, e.getMessage());
                    updateOcrStatus(driverId, "FAILED");
                }

            } else {
                log.warn("OCR extraction failed for driver: {}", driverId);
                updateOcrStatus(driverId, "FAILED");
            }

        } catch (Exception e) {
            log.error("Async OCR processing failed for driver {}: {}", driverId, e.getMessage());
            updateOcrStatus(driverId, "FAILED");
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Update OCR status in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOcrStatus(UUID driverId, String status) {
        try {
            Driver driver = driverRepository.findById(driverId).orElse(null);
            if (driver != null) {
                driver.setProfileExtractionStatus(status);
                driverRepository.save(driver);
                log.info("Updated OCR status for driver {}: {}", driverId, status);
            }
        } catch (Exception e) {
            log.error("Failed to update OCR status for driver {}: {}", driverId, e.getMessage());
        }
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

                // Use separate method call instead of lambda
                startAsyncFaceVerificationDetached(driver.getDriverId());
            }
        } catch (Exception e) {
            log.error("Failed to check face verification readiness: {}", e.getMessage());
        }
    }

    /**
     * Start async face verification - completely detached
     */
    private void startAsyncFaceVerificationDetached(UUID driverId) {
        new Thread(() -> {
            try {
                Thread.sleep(200);
                startFaceVerificationAsync(driverId);
            } catch (Exception e) {
                log.error("Failed to start async face verification: {}", e.getMessage());
            }
        }, "FaceVerification-" + driverId).start();
    }

    /**
     * Async face verification - COMPLETELY SEPARATE TRANSACTION
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
                updateFaceVerificationStatus(driverId, "PENDING", 0.0);
                return CompletableFuture.completedFuture(null);
            }

            // Update status
            updateFaceVerificationStatus(driverId, "IN_PROGRESS", null);
            incrementFaceVerificationAttempts(driverId);

            // Perform face verification with timeout
            performFaceVerificationWithTimeout(driver);

        } catch (Exception e) {
            log.error("Async face verification failed for driver {}: {}", driverId, e.getMessage());
            updateFaceVerificationStatus(driverId, "FAILED", 0.0);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Update face verification status in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFaceVerificationStatus(UUID driverId, String status, Double score) {
        try {
            Driver driver = driverRepository.findById(driverId).orElse(null);
            if (driver != null) {
                driver.setFaceVerificationStatus(status);
                if (score != null) {
                    driver.setFaceMatchScore(score);
                }
                driverRepository.save(driver);
                log.info("Updated face verification status for driver {}: {} (score: {})", driverId, status, score);
            }
        } catch (Exception e) {
            log.error("Failed to update face verification status for driver {}: {}", driverId, e.getMessage());
        }
    }

    /**
     * Increment face verification attempts in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementFaceVerificationAttempts(UUID driverId) {
        try {
            Driver driver = driverRepository.findById(driverId).orElse(null);
            if (driver != null) {
                int attempts = driver.getFaceVerificationAttempts() != null ?
                        driver.getFaceVerificationAttempts() + 1 : 1;
                driver.setFaceVerificationAttempts(attempts);
                driverRepository.save(driver);
                log.info("Incremented face verification attempts for driver {}: {}", driverId, attempts);
            }
        } catch (Exception e) {
            log.error("Failed to increment face verification attempts for driver {}: {}", driverId, e.getMessage());
        }
    }

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
                updateFaceVerificationStatus(driver.getDriverId(), "FAILED", 0.0);
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

            // Use 0.5 threshold (50%) as per requirement
            double threshold = 0.5;

            // FIXED: Check for actual match result, not just error message
            if (faceMatchResult.getMatch() != null && faceMatchResult.getMatch()) {
                // We have a successful match
                if (confidenceScore > threshold) {
                    updateFaceVerificationStatus(driver.getDriverId(), "VERIFIED", confidenceScore);
                    log.info("Face verification PASSED for driver: {} with confidence: {:.1%}",
                            driver.getDriverId(), confidenceScore);
                } else {
                    updateFaceVerificationStatus(driver.getDriverId(), "FAILED", confidenceScore);
                    log.info("Face verification FAILED for driver: {} with confidence: {:.1%} (at or below threshold: {:.1%})",
                            driver.getDriverId(), confidenceScore, threshold);
                }
            } else {
                // No match or error occurred
                String errorMessage = faceMatchResult.getErrorMessage();
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    log.error("Face verification error for driver {}: {}", driver.getDriverId(), errorMessage);
                } else {
                    log.info("Face verification no match for driver: {} with confidence: {:.1%}",
                            driver.getDriverId(), confidenceScore);
                }
                updateFaceVerificationStatus(driver.getDriverId(), "FAILED", confidenceScore);
            }

        } catch (Exception e) {
            log.error("Face verification processing failed for driver {}: {}", driver.getDriverId(), e.getMessage());
            updateFaceVerificationStatus(driver.getDriverId(), "FAILED", 0.0);
        }
    }

    /**
     * FIXED: Single convertUrlToAbsolutePath method
     */
    private String convertUrlToAbsolutePath(String relativeUrl) {
        String cleanPath = relativeUrl.startsWith("/") ? relativeUrl.substring(1) : relativeUrl;
        return System.getProperty("user.dir") + "/" + cleanPath.replace("\\", "/");
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

    /**
     * Process document based on type - MODIFIED for async handling
     */
    private String processDocument(Driver driver, DocumentType documentType, MultipartFile file, String fileUrl) {
        updateDriverDocumentUrl(driver, documentType, fileUrl);

        // ENHANCED: Update profile photo URL for selfie uploads
        if (documentType == DocumentType.SELFIE) {
            User user = driver.getUser();
            if (user != null) {
                user.setProfilePhotoUrl(fileUrl);
                userRepository.save(user);
                log.info("Updated profile photo URL for driver: {}", driver.getDriverId());
            }
        }

        if (documentType == DocumentType.SELFIE) {
            processSelfie(driver);
            return "Selfie uploaded successfully. Profile photo updated.";
        } else if (documentType == DocumentType.DRIVING_LICENSE) {
            return "OCR processing started asynchronously.";
        } else if (EnumSet.of(
                DocumentType.REVENUE_LICENSE,
                DocumentType.VEHICLE_REGISTRATION,
                DocumentType.VEHICLE_INSURANCE
        ).contains(documentType)) {
            processOtherDocument(driver, documentType);
            return documentType.name() + " uploaded successfully.";
        } else {
            return "Unsupported document type: " + documentType.name();
        }

    }

    /**
     * Process selfie upload - FIXED to avoid lambda issues
     */
    private void processSelfie(Driver driver) {
        log.info("Processing selfie for driver: {}", driver.getDriverId());

        // Update the user's profile photo URL with the selfie URL
        User user = driver.getUser();
        if (user != null && driver.getSelfieUrl() != null) {
            user.setProfilePhotoUrl(driver.getSelfieUrl());
            userRepository.save(user);
            log.info("Updated profile photo URL for driver: {}", driver.getDriverId());
        }

        driver.setFaceVerificationStatus("PENDING");

        // If driving license OCR is completed, start async face verification
        if (driver.getDrivingLicenseUrl() != null &&
                "COMPLETED".equals(driver.getProfileExtractionStatus())) {

            log.info("Driving license OCR already completed - starting async face verification");

            // FIXED: Use the correct method name
            startAsyncFaceVerificationDetached(driver.getDriverId());
        } else {
            log.info("Selfie uploaded, waiting for driving license OCR completion");
        }
    }

    private void processOtherDocument(Driver driver, DocumentType documentType) {
        driver.setDocumentVerificationStatus("IN_PROGRESS");
        log.info("{} uploaded for driver: {}", documentType, driver.getDriverId());
    }

    /**
     * FIXED: Update driver document URL method - simple if-else statements
     */
    private void updateDriverDocumentUrl(Driver driver, DocumentType documentType, String fileUrl) {
        if (documentType == DocumentType.SELFIE) {
            driver.setSelfieUrl(fileUrl);
        } else if (documentType == DocumentType.DRIVING_LICENSE) {
            driver.setDrivingLicenseUrl(fileUrl);
        } else if (documentType == DocumentType.REVENUE_LICENSE) {
            driver.setRevenueLicenseUrl(fileUrl);
        } else if (documentType == DocumentType.VEHICLE_REGISTRATION) {
            driver.setVehicleRegistrationUrl(fileUrl);
        } else if (documentType == DocumentType.VEHICLE_INSURANCE) {
            driver.setVehicleInsuranceUrl(fileUrl);
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
            if ("VERIFIED".equals(driver.getFaceVerificationStatus())) {
                double score = driver.getFaceMatchScore() != null ? driver.getFaceMatchScore() : 0.0;
                return String.format("Document uploaded and face verification passed! (Confidence: %.1f%%)", score * 100);
            } else if ("FAILED".equals(driver.getFaceVerificationStatus())) {
                double failScore = driver.getFaceMatchScore() != null ? driver.getFaceMatchScore() : 0.0;
                return String.format("Document uploaded but face verification failed (Confidence: %.1f%% - at or below 50%% threshold). Please ensure clear photos.", failScore * 100);
            } else if ("MANUAL_REVIEW".equals(driver.getFaceVerificationStatus())) {
                double reviewScore = driver.getFaceMatchScore() != null ? driver.getFaceMatchScore() : 0.0;
                return String.format("Document uploaded. Face verification under manual review (Confidence: %.1f%%).", reviewScore * 100);
            } else if ("IN_PROGRESS".equals(driver.getFaceVerificationStatus())) {
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