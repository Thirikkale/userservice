package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.response.DocumentUploadResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Driver;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.model.Vehicle;
import com.thirikkale.userservice.model.enums.DocumentType;
import com.thirikkale.userservice.repository.DriverRepository;
import com.thirikkale.userservice.repository.UserRepository;
import com.thirikkale.userservice.repository.VehicleRepository;
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
    private final VehicleRepository vehicleRepository;
    private final FileStorageService fileStorageService;
    private final OCRService ocrService;
    private final FaceVerificationService faceVerificationService;
    private final DriverService driverService;

    /**
     * Upload driver personal document (selfie, driving license)
     */
    @Transactional
    public DocumentUploadResponse uploadDriverDocument(UUID driverId, DocumentType documentType, MultipartFile file) {
        log.info("Processing personal document upload for driver {} - type: {}", driverId, documentType);

        try {
            fileStorageService.validateFile(file);

            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

            // Store the file
            String fileUrl = fileStorageService.storeDriverDocument(driverId, documentType, file);

            // Update driver with document URL
            updateDriverDocumentUrl(driver, documentType, fileUrl);

            // Process based on document type
            String extractedText = processDocument(driver, documentType, file, fileUrl);

            // Update document upload status
            updateDocumentUploadStatus(driver);

            // Save driver
            driverRepository.save(driver);

            log.info("Personal document uploaded successfully for driver: {} - type: {}", driverId, documentType);
            return buildDocumentUploadResponse(driver, documentType, fileUrl, extractedText);

        } catch (Exception e) {
            log.error("Failed to upload personal document for driver {}: {}", driverId, e.getMessage());
            return DocumentUploadResponse.failure(driverId, documentType,
                    "Document upload failed: " + e.getMessage());
        }
    }

    /**
     * Upload vehicle-specific document
     */
    @Transactional
    public DocumentUploadResponse uploadVehicleDocument(UUID driverId, UUID vehicleId,
                                                        DocumentType documentType, MultipartFile file) {
        log.info("Processing vehicle document upload for driver {} vehicle {} - type: {}",
                driverId, vehicleId, documentType);

        try {
            fileStorageService.validateFile(file);

            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

            Vehicle vehicle = vehicleRepository.findByDriverIdAndVehicleId(driverId, vehicleId)
                    .orElseThrow(() -> new CustomExceptions.VehicleNotFoundException("Vehicle not found"));

            // Store the file
            String fileUrl = fileStorageService.storeDriverDocument(driverId, documentType, file);

            // Update vehicle with document URL
            updateVehicleDocumentUrl(vehicle, documentType, fileUrl);

            // Update vehicle document upload status
            updateVehicleDocumentUploadStatus(vehicle);

            // Save vehicle
            vehicleRepository.save(vehicle);

            log.info("Vehicle document uploaded successfully for driver: {} vehicle: {} - type: {}",
                    driverId, vehicleId, documentType);

            return buildVehicleDocumentUploadResponse(driver, vehicle, documentType, fileUrl);

        } catch (Exception e) {
            log.error("Failed to upload vehicle document for driver {} vehicle {}: {}",
                    driverId, vehicleId, e.getMessage());
            return DocumentUploadResponse.failure(driverId, documentType,
                    "Vehicle document upload failed: " + e.getMessage());
        }
    }

    /**
     * Start async OCR processing for driving license
     */
    private void startAsyncOcrProcessingDetached(UUID driverId, String fileUrl) {
        new Thread(() -> {
            try {
                Thread.sleep(100);
                processDriverLicenseAsync(driverId, fileUrl);
            } catch (Exception e) {
                log.error("Failed to start async OCR processing for driver {}: {}", driverId, e.getMessage());
            }
        }, "OCR-Processing-" + driverId).start();
    }

    /**
     * Async OCR processing for driving license
     */
    @Async("aiProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> processDriverLicenseAsync(UUID driverId, String fileUrl) {
        log.info("Starting async OCR processing for driver: {}", driverId);

        try {
            Driver driver = driverRepository.findById(driverId).orElse(null);
            if (driver == null) {
                log.error("Driver not found for OCR processing: {}", driverId);
                return CompletableFuture.completedFuture(null);
            }

            updateOcrStatus(driverId, "IN_PROGRESS");

            String absolutePath = convertUrlToAbsolutePath(fileUrl);
            Path filePath = Paths.get(absolutePath);

            if (!Files.exists(filePath)) {
                log.error("File not found for OCR processing: {}", absolutePath);
                updateOcrStatus(driverId, "FAILED");
                return CompletableFuture.completedFuture(null);
            }

            OCRService.DrivingLicenseInfo licenseInfo = ocrService.extractDrivingLicenseInfoFromPath(filePath);

            if (licenseInfo != null && licenseInfo.getFirstName() != null) {
                driverService.updateDriverProfileFromOCR(
                        driverId,
                        licenseInfo.getFirstName(),
                        licenseInfo.getLastName(),
                        licenseInfo.getLicenseNumber(),
                        licenseInfo.getExpiryDate()
                );

                updateOcrStatus(driverId, "COMPLETED");
                startFaceVerificationIfReady(driver);
            } else {
                updateOcrStatus(driverId, "FAILED");
            }

        } catch (Exception e) {
            log.error("OCR processing failed for driver {}: {}", driverId, e.getMessage());
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
            if (driver.getSelfieUrl() != null && driver.getDrivingLicenseUrl() != null &&
                    "COMPLETED".equals(driver.getProfileExtractionStatus()) &&
                    !"VERIFIED".equals(driver.getFaceVerificationStatus())) {

                startAsyncFaceVerificationDetached(driver.getDriverId());
            }
        } catch (Exception e) {
            log.error("Failed to check face verification readiness for driver {}: {}",
                    driver.getDriverId(), e.getMessage());
        }
    }

    /**
     * Start async face verification - completely detached
     */
    private void startAsyncFaceVerificationDetached(UUID driverId) {
        new Thread(() -> {
            try {
                Thread.sleep(500);
                startFaceVerificationAsync(driverId);
            } catch (Exception e) {
                log.error("Failed to start async face verification for driver {}: {}", driverId, e.getMessage());
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
            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found: " + driverId));

            if (driver.getSelfieUrl() == null || driver.getDrivingLicenseUrl() == null) {
                log.warn("Cannot start face verification - missing images for driver: {}", driverId);
                updateFaceVerificationStatus(driverId, "PENDING", 0.0);
                return CompletableFuture.completedFuture(null);
            }

            updateFaceVerificationStatus(driverId, "IN_PROGRESS", null);
            incrementFaceVerificationAttempts(driverId);

            performFaceVerificationWithTimeout(driver);

        } catch (Exception e) {
            log.error("Face verification failed for driver {}: {}", driverId, e.getMessage());
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
            String selfieAbsolutePath = convertUrlToAbsolutePath(driver.getSelfieUrl());
            String licenseAbsolutePath = convertUrlToAbsolutePath(driver.getDrivingLicenseUrl());

            log.info("Face verification files - Selfie: {}, License: {}", selfieAbsolutePath, licenseAbsolutePath);

            if (!fileStorageService.fileExists(selfieAbsolutePath) ||
                    !fileStorageService.fileExists(licenseAbsolutePath)) {

                log.error("Face verification files not found for driver: {}", driver.getDriverId());
                updateFaceVerificationStatus(driver.getDriverId(), "FAILED", 0.0);
                return;
            }

            var faceMatchResult = faceVerificationService.verifyFaceMatch(
                    selfieAbsolutePath,
                    licenseAbsolutePath
            );

            double confidenceScore = faceMatchResult.getConfidenceScore() != null ?
                    faceMatchResult.getConfidenceScore() : 0.0;

            double threshold = 0.5;

            if (faceMatchResult.getMatch() != null && faceMatchResult.getMatch()) {
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
     * Convert URL to absolute path
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
     * Process document based on type
     */
    private String processDocument(Driver driver, DocumentType documentType, MultipartFile file, String fileUrl) {
        switch (documentType) {
            case SELFIE:
                processSelfie(driver);
                return "Selfie uploaded for face verification";

            case DRIVING_LICENSE:
                startAsyncOcrProcessingDetached(driver.getDriverId(), fileUrl);
                return "Driving license uploaded - extracting information...";

            default:
                return "Document uploaded successfully";
        }
    }

    /**
     * Process selfie upload
     */
    private void processSelfie(Driver driver) {
        if (driver.getDrivingLicenseUrl() != null &&
                "COMPLETED".equals(driver.getProfileExtractionStatus())) {
            startAsyncFaceVerificationDetached(driver.getDriverId());
        }
    }

    /**
     * Update driver document URL
     */
    private void updateDriverDocumentUrl(Driver driver, DocumentType documentType, String fileUrl) {
        if (documentType == DocumentType.SELFIE) {
            driver.setSelfieUrl(fileUrl);
        } else if (documentType == DocumentType.DRIVING_LICENSE) {
            driver.setDrivingLicenseUrl(fileUrl);
        }
    }

    /**
     * Update vehicle document URL
     */
    private void updateVehicleDocumentUrl(Vehicle vehicle, DocumentType documentType, String fileUrl) {
        if (documentType == DocumentType.REVENUE_LICENSE) {
            vehicle.setRevenueLicenseUrl(fileUrl);
        } else if (documentType == DocumentType.VEHICLE_REGISTRATION) {
            vehicle.setVehicleRegistrationUrl(fileUrl);
        } else if (documentType == DocumentType.VEHICLE_INSURANCE) {
            vehicle.setVehicleInsuranceUrl(fileUrl);
        }
    }

    /**
     * Update document upload status for driver
     */
    private void updateDocumentUploadStatus(Driver driver) {
        boolean allPersonalDocumentsUploaded = driver.getSelfieUrl() != null &&
                driver.getDrivingLicenseUrl() != null;
        driver.setIsDocumentsUploaded(allPersonalDocumentsUploaded);
    }

    /**
     * Update document upload status for vehicle
     */
    private void updateVehicleDocumentUploadStatus(Vehicle vehicle) {
        boolean allVehicleDocumentsUploaded = vehicle.getRevenueLicenseUrl() != null &&
                vehicle.getVehicleRegistrationUrl() != null &&
                vehicle.getVehicleInsuranceUrl() != null;
        vehicle.setIsDocumentsUploaded(allVehicleDocumentsUploaded);
    }

    /**
     * Build document upload response for driver documents
     */
    private DocumentUploadResponse buildDocumentUploadResponse(Driver driver, DocumentType documentType,
                                                               String fileUrl, String extractedText) {
        return DocumentUploadResponse.builder()
                .driverId(driver.getDriverId())
                .documentType(documentType)
                .fileUrl(fileUrl)
                .uploaded(true)
                .message(determineResponseMessage(driver, documentType))
                .verificationProgress(driver.getVerificationProgress())
                .nextStep(getNextStep(driver))
                .profileExtractionStatus(driver.getProfileExtractionStatus())
                .faceVerificationStatus(driver.getFaceVerificationStatus())
                .faceVerificationAttempts(driver.getFaceVerificationAttempts())
                .extractedText(extractedText)
                .build();
    }

    /**
     * Build document upload response for vehicle documents
     */
    private DocumentUploadResponse buildVehicleDocumentUploadResponse(Driver driver, Vehicle vehicle,
                                                                      DocumentType documentType, String fileUrl) {
        return DocumentUploadResponse.builder()
                .driverId(driver.getDriverId())
                .documentType(documentType)
                .fileUrl(fileUrl)
                .uploaded(true)
                .message("Vehicle document uploaded successfully")
                .verificationProgress(vehicle.getVerificationProgress())
                .nextStep(getVehicleNextStep(vehicle))
                .build();
    }

    private String determineResponseMessage(Driver driver, DocumentType documentType) {
        if (documentType == DocumentType.SELFIE) {
            return "Selfie uploaded successfully";
        } else if (documentType == DocumentType.DRIVING_LICENSE) {
            return "Driving license uploaded - processing in background";
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
            return "All personal documents verified. Upload vehicle documents to complete verification.";
        } else {
            return "Continue with verification process";
        }
    }

    private String getVehicleNextStep(Vehicle vehicle) {
        if (!vehicle.getIsDocumentsUploaded()) {
            return "Continue uploading remaining vehicle documents";
        } else if ("PENDING".equals(vehicle.getVerificationStatus())) {
            return "Vehicle document verification in progress";
        } else if ("VERIFIED".equals(vehicle.getVerificationStatus())) {
            return "Vehicle verification complete";
        } else {
            return "Continue with vehicle verification";
        }
    }

    private void validateFile(MultipartFile file, DocumentType documentType) {
        if (file.isEmpty()) {
            throw new CustomExceptions.InvalidFileException("File is empty");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new CustomExceptions.InvalidFileException("File size too large. Maximum 10MB allowed.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomExceptions.InvalidFileException("Only image files are allowed");
        }
    }
}