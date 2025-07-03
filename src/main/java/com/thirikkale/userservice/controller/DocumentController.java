package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.response.DocumentResponse;
import com.thirikkale.userservice.model.enums.DocumentType;
import com.thirikkale.userservice.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Document Management", description = "Driver document upload and verification operations")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    @Operation(summary = "Upload driver document")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam UUID driverId,
            @RequestParam DocumentType documentType,
            @RequestParam("file") MultipartFile file) {
        log.info("Document upload request - Driver: {}, Type: {}", driverId, documentType);
        DocumentResponse response = documentService.uploadDocument(driverId, documentType, file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/driver/{driverId}")
    @Operation(summary = "Get all documents for a driver")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<List<DocumentResponse>> getDriverDocuments(@PathVariable UUID driverId) {
        log.info("Get driver documents request: {}", driverId);
        List<DocumentResponse> response = documentService.getDriverDocuments(driverId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending")
    @Operation(summary = "Get all pending documents for verification")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<List<DocumentResponse>> getPendingDocuments() {
        log.info("Get pending documents request");
        List<DocumentResponse> response = documentService.getPendingDocuments();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{documentId}/verify")
    @Operation(summary = "Verify a document")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<DocumentResponse> verifyDocument(
            @PathVariable UUID documentId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String notes,
            @RequestParam UUID verifiedBy) {
        log.info("Verify document request - ID: {}, Approved: {}", documentId, approved);
        DocumentResponse response = documentService.verifyDocument(documentId, approved, notes, verifiedBy);
        return ResponseEntity.ok(response);
    }
}