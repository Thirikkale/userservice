package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.response.DocumentResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Document;
import com.thirikkale.userservice.model.Driver;
import com.thirikkale.userservice.model.enums.DocumentStatus;
import com.thirikkale.userservice.model.enums.DocumentType;
import com.thirikkale.userservice.repository.DocumentRepository;
import com.thirikkale.userservice.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DriverRepository driverRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public DocumentResponse uploadDocument(UUID driverId, DocumentType documentType, MultipartFile file) {
        log.info("Uploading document for driver: {} - Type: {}", driverId, documentType);

        // Verify driver exists
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        // Check if document already exists
        if (documentRepository.existsByDriverIdAndDocumentType(driverId, documentType)) {
            throw new CustomExceptions.DocumentUploadException("Document of this type already exists for driver");
        }

        try {
            // Store file
            String fileUrl = fileStorageService.storeFile(file, "documents", driverId.toString());

            // Create document record
            Document document = Document.builder()
                    .driverId(driverId)
                    .documentType(documentType)
                    .fileUrl(fileUrl)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .status(DocumentStatus.PENDING)
                    .build();

            document = documentRepository.save(document);

            // Update driver's document upload status
            updateDriverDocumentStatus(driverId);

            log.info("Document uploaded successfully: {}", document.getId());
            return mapToDocumentResponse(document);

        } catch (Exception e) {
            log.error("Failed to upload document for driver {}: {}", driverId, e.getMessage());
            throw new CustomExceptions.DocumentUploadException("Failed to upload document: " + e.getMessage());
        }
    }

    public List<DocumentResponse> getDriverDocuments(UUID driverId) {
        log.info("Getting documents for driver: {}", driverId);

        List<Document> documents = documentRepository.findByDriverId(driverId);
        return documents.stream()
                .map(this::mapToDocumentResponse)
                .collect(Collectors.toList());
    }

    public List<DocumentResponse> getPendingDocuments() {
        log.info("Getting pending documents for verification");

        List<Document> documents = documentRepository.findByStatus(DocumentStatus.PENDING);
        return documents.stream()
                .map(this::mapToDocumentResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DocumentResponse verifyDocument(UUID documentId, boolean approved, String notes, UUID verifiedBy) {
        log.info("Verifying document: {} - Approved: {}", documentId, approved);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Document not found"));

        document.setStatus(approved ? DocumentStatus.VERIFIED : DocumentStatus.REJECTED);
        document.setVerificationNotes(notes);
        document.setVerifiedBy(verifiedBy);
        document.setVerifiedAt(LocalDateTime.now());

        document = documentRepository.save(document);

        // Update driver's verification status if all documents are verified
        updateDriverVerificationStatus(document.getDriverId());

        log.info("Document verification completed: {}", documentId);
        return mapToDocumentResponse(document);
    }

    private void updateDriverDocumentStatus(UUID driverId) {
        // Check if all required documents are uploaded
        boolean allDocumentsUploaded = checkAllRequiredDocumentsUploaded(driverId);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        driver.setIsDocumentsUploaded(allDocumentsUploaded);
        driverRepository.save(driver);

        log.info("Driver document upload status updated: {} - {}", driverId, allDocumentsUploaded);
    }

    private void updateDriverVerificationStatus(UUID driverId) {
        // Check if all documents are verified
        long verifiedCount = documentRepository.countByDriverIdAndStatus(driverId, DocumentStatus.VERIFIED);
        long totalCount = documentRepository.findByDriverId(driverId).size();
        long rejectedCount = documentRepository.countByDriverIdAndStatus(driverId, DocumentStatus.REJECTED);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        if (rejectedCount > 0) {
            driver.setFaceVerificationStatus("REJECTED");
            driver.setIsVerified(false);
        } else if (verifiedCount == totalCount && totalCount >= 5) { // All 5 required documents
            driver.setFaceVerificationStatus("VERIFIED");
            driver.setIsVerified(true);
            driver.setVerificationDate(LocalDateTime.now());
        }

        driverRepository.save(driver);
        log.info("Driver verification status updated: {}", driverId);
    }

    private boolean checkAllRequiredDocumentsUploaded(UUID driverId) {
        DocumentType[] requiredTypes = {
                DocumentType.SELFIE,
                DocumentType.REVENUE_LICENSE,
                DocumentType.VEHICLE_REGISTRATION,
                DocumentType.VEHICLE_INSURANCE,
                DocumentType.DRIVING_LICENSE
        };

        for (DocumentType type : requiredTypes) {
            if (!documentRepository.existsByDriverIdAndDocumentType(driverId, type)) {
                return false;
            }
        }
        return true;
    }

    private DocumentResponse mapToDocumentResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .driverId(document.getDriverId())
                .documentType(document.getDocumentType())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .status(document.getStatus())
                .verificationNotes(document.getVerificationNotes())
                .verifiedBy(document.getVerifiedBy())
                .verifiedAt(document.getVerifiedAt())
                .uploadedAt(document.getCreatedAt())
                .build();
    }
}