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
     * Upload driver document and trigger appropriate processing - ENHANCED
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
            log.info("File stored at: {}", fileUrl);

            // 4. Update driver with document URL
            updateDriverDocumentUrl(driver, documentType, fileUrl);

            // 5. Initialize face verification attempts if null
            if (driver.getFaceVerificationAttempts() == null) {
                driver.setFaceVerificationAttempts(0);
            }

            // 6. Process the document based on type
            String extractedText = processDocument(driver, documentType, file, fileUrl);

            // 7. Update overall document upload status
            updateDocumentUploadStatus(driver);

            // 8. Save driver changes
            driver = driverRepository.save(driver);

            // 9. Build comprehensive response with extracted text
            return buildDocumentUploadResponse(driver, documentType, fileUrl, extractedText);

        } catch (Exception e) {
            log.error("Document upload failed for driver {} - type {}: {}", driverId, documentType, e.getMessage());
            throw new CustomExceptions.DocumentUploadException("Document upload failed: " + e.getMessage());
        }
    }

    /**
     * Process document based on type - ENHANCED to return extracted text
     */
    private String processDocument(Driver driver, DocumentType documentType, MultipartFile file, String fileUrl) {
        switch (documentType) {
            case SELFIE:
                processSelfie(driver);
                return null;

            case DRIVING_LICENSE:
                return processDrivingLicense(driver, file, fileUrl);

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
     * Process selfie upload - ENHANCED with better verification trigger
     */
    private void processSelfie(Driver driver) {
        log.info("Processing selfie for driver: {}", driver.getDriverId());

        // Set initial status
        driver.setFaceVerificationStatus("PENDING");

        // If driving license is already uploaded AND OCR completed, start face verification
        if (driver.getDrivingLicenseUrl() != null &&
                "COMPLETED".equals(driver.getProfileExtractionStatus())) {

            log.info("Both selfie and driving license available with OCR completed - starting face verification");
            startFaceVerification(driver);
        } else {
            log.info("Selfie uploaded, waiting for driving license OCR completion to start face verification");
        }
    }

    /**
     * Process driving license - ENHANCED with better OCR integration and text return
     */
    private String processDrivingLicense(Driver driver, MultipartFile file, String fileUrl) {
        try {
            log.info("Starting OCR processing for driving license: {}", driver.getDriverId());

            // Update status to processing
            driver.setProfileExtractionStatus("IN_PROGRESS");

            // Extract information from driving license using OCR
            var ocrResult = ocrService.extractDrivingLicenseInfo(file);

            if (ocrResult != null && ocrResult.getFirstName() != null) {
                // Update driver profile with extracted information
                log.info("OCR extraction successful, updating driver profile...");

                driverService.updateDriverProfileFromOCR(
                        driver.getDriverId(),
                        ocrResult.getFirstName(),
                        ocrResult.getLastName(),
                        ocrResult.getLicenseNumber(),
                        ocrResult.getExpiryDate()
                );

                // Mark OCR as completed
                driver.setProfileExtractionStatus("COMPLETED");
                log.info("OCR extraction completed successfully for driver: {}", driver.getDriverId());

                // If selfie is already uploaded, start face verification
                if (driver.getSelfieUrl() != null) {
                    log.info("Both driving license and selfie available with OCR completed - starting face verification");
                    startFaceVerification(driver);
                } else {
                    log.info("Driving license processed, waiting for selfie to complete face verification");
                    driver.setFaceVerificationStatus("PENDING");
                }

                // Return extracted text for response
                return ocrResult.getExtractedText();
            } else {
                log.warn("OCR extraction failed - no valid data extracted for driver: {}", driver.getDriverId());
                driver.setProfileExtractionStatus("FAILED");
                driver.setFaceVerificationStatus("PENDING");
                return "OCR extraction failed - no valid data found";
            }

        } catch (Exception e) {
            log.error("OCR processing failed for driver {}: {}", driver.getDriverId(), e.getMessage());
            driver.setProfileExtractionStatus("FAILED");
            driver.setFaceVerificationStatus("PENDING"); // Can retry after fixing OCR
            return "OCR processing failed: " + e.getMessage();
        }
    }

    /**
     * Process other documents
     */
    private void processOtherDocument(Driver driver, DocumentType documentType) {
        driver.setDocumentVerificationStatus("IN_PROGRESS");
        log.info("{} uploaded for driver: {}", documentType, driver.getDriverId());
    }

    // Add this method to your existing DriverDocumentService:

    /**
     * Start face verification - ENHANCED with better error handling and service checking
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

            // Check if FastAPI service is available
//            if (!PythonIntegrationService.isServiceAvailable()) {
//                log.error("FastAPI AI service is not available for driver: {}", driver.getDriverId());
//                driver.setFaceVerificationStatus("FAILED");
//                driver.setFaceMatchScore(0.0);
//                return;
//            }

            // Update status and increment attempts
            driver.setFaceVerificationStatus("IN_PROGRESS");
            driver.setFaceVerificationAttempts(driver.getFaceVerificationAttempts() + 1);

            // Convert relative URLs to absolute file paths
            String selfieAbsolutePath = convertUrlToAbsolutePath(driver.getSelfieUrl());
            String licenseAbsolutePath = convertUrlToAbsolutePath(driver.getDrivingLicenseUrl());

            log.info("Face verification files - Selfie: {}, License: {}", selfieAbsolutePath, licenseAbsolutePath);

            // Verify both files exist
            if (!fileStorageService.fileExists(selfieAbsolutePath)) {
                log.error("Selfie file not found: {}", selfieAbsolutePath);
                driver.setFaceVerificationStatus("FAILED");
                driver.setFaceMatchScore(0.0);
                return;
            }

            if (!fileStorageService.fileExists(licenseAbsolutePath)) {
                log.error("License file not found: {}", licenseAbsolutePath);
                driver.setFaceVerificationStatus("FAILED");
                driver.setFaceMatchScore(0.0);
                return;
            }

            // Perform face matching between selfie and driving license photo
            var faceMatchResult = faceVerificationService.verifyFaceMatch(
                    selfieAbsolutePath,
                    licenseAbsolutePath
            );

            // Detailed logging of face verification result
            log.info("Face verification completed for driver {}: match={}, confidence={}, error={}",
                    driver.getDriverId(),
                    faceMatchResult.isMatch(),
                    faceMatchResult.getConfidenceScore(),
                    faceMatchResult.getErrorMessage());

            // Store the match score
            double confidenceScore = faceMatchResult.getConfidenceScore() != null ?
                    faceMatchResult.getConfidenceScore() : 0.0;
            driver.setFaceMatchScore(confidenceScore);

            // Check for errors first
            if (faceMatchResult.getErrorMessage() != null && !faceMatchResult.getErrorMessage().isEmpty()) {
                log.error("Face verification error for driver {}: {}", driver.getDriverId(), faceMatchResult.getErrorMessage());
                driver.setFaceVerificationStatus("FAILED");
                return;
            }

            // FIXED: Use the same threshold logic as FastAPI service (0.5)
            double threshold = 0.5; // Match FastAPI service threshold

            log.info("Face verification result for driver {}: match={}, confidence={}, threshold={}",
                    driver.getDriverId(), faceMatchResult.isMatch(), confidenceScore, threshold);

            if (confidenceScore >= threshold) {
                if (confidenceScore >= 0.8) {
                    // High confidence match
                    driver.setFaceVerificationStatus("VERIFIED");
                    log.info("Face verification PASSED (High Confidence) for driver: {} with confidence: {}",
                            driver.getDriverId(), confidenceScore);
                } else if (confidenceScore >= 0.65) {
                    // Good confidence match
                    driver.setFaceVerificationStatus("VERIFIED");
                    log.info("Face verification PASSED (Good Confidence) for driver: {} with confidence: {}",
                            driver.getDriverId(), confidenceScore);
                } else {
                    // Borderline case - send for manual review
                    driver.setFaceVerificationStatus("MANUAL_REVIEW");
                    log.info("Face verification requires MANUAL_REVIEW for driver: {} with confidence: {}",
                            driver.getDriverId(), confidenceScore);
                }
            } else {
                // Low confidence - failed
                driver.setFaceVerificationStatus("FAILED");
                log.info("Face verification FAILED for driver: {} with confidence: {} (below threshold: {})",
                        driver.getDriverId(), confidenceScore, threshold);
            }

        } catch (Exception e) {
            log.error("Face verification failed for driver {}: {}", driver.getDriverId(), e.getMessage(), e);
            driver.setFaceVerificationStatus("FAILED");
            driver.setFaceMatchScore(0.0);
        }
    }

    /**
     * Convert relative URL to absolute file path
     */
    private String convertUrlToAbsolutePath(String relativeUrl) {
        // Remove leading slash if present
        String cleanPath = relativeUrl.startsWith("/") ? relativeUrl.substring(1) : relativeUrl;

        // Convert to absolute path using forward slashes (Windows handles both)
        return System.getProperty("user.dir") + "/" + cleanPath.replace("\\", "/");
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
     * Build comprehensive response - ENHANCED with extracted text
     */
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

        // Add face verification details if available
        if (driver.getFaceMatchScore() != null) {
            responseBuilder.faceMatchScore(driver.getFaceMatchScore());
        }

        if (driver.getFaceVerificationStatus() != null) {
            responseBuilder.faceVerificationStatus(driver.getFaceVerificationStatus());
        }

        if (driver.getFaceVerificationAttempts() != null) {
            responseBuilder.faceVerificationAttempts(driver.getFaceVerificationAttempts());
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

            // Add extracted text if available
            if (extractedText != null) {
                responseBuilder.extractedText(extractedText);
            }
        }

        // Add document verification status
        if (driver.getDocumentVerificationStatus() != null) {
            responseBuilder.documentVerificationStatus(driver.getDocumentVerificationStatus());
        }

        return responseBuilder.build();
    }

    /**
     * Determine appropriate response message - ENHANCED with better face verification feedback
     */
    private String determineResponseMessage(Driver driver, DocumentType documentType) {
        if (documentType == DocumentType.DRIVING_LICENSE) {
            if ("FAILED".equals(driver.getProfileExtractionStatus())) {
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