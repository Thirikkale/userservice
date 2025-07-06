package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.request.DriverRegistrationRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.dto.response.DocumentUploadResponse;
import com.thirikkale.userservice.dto.response.DriverResponse;
import com.thirikkale.userservice.model.enums.DocumentType;
import com.thirikkale.userservice.service.DriverDocumentService;
import com.thirikkale.userservice.service.DriverService;
import com.thirikkale.userservice.service.MultiRoleAuthService;
import com.thirikkale.userservice.service.MultiRoleLoginService;
import com.thirikkale.userservice.exception.CustomExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Driver Management", description = "Driver registration, authentication, document management and operations")
public class DriverController {

    private final DriverService driverService;
    private final DriverDocumentService driverDocumentService;
    private final MultiRoleAuthService multiRoleAuthService;
    private final MultiRoleLoginService multiRoleLoginService;

    @PostMapping("/register")
    @Operation(
            summary = "Register for Driver App (Step 1)",
            description = "Register new driver or upgrade existing rider to driver using Firebase Phone Auth token. " +
                    "This creates a basic profile. Driver must then upload required documents for verification."
    )
    public ResponseEntity<AuthResponse> registerDriver(@Valid @RequestBody DriverRegistrationRequest request) {
        log.info("Driver app registration request received");

        try {
            AuthResponse response = multiRoleAuthService.registerUser(
                    request.getFirebaseIdToken(),
                    request.getFirstName(),
                    request.getLastName(),
                    request.getWhatsappNumber(),
                    MultiRoleAuthService.AppType.DRIVER_APP
            );

            return ResponseEntity.ok(response);

        } catch (CustomExceptions.UserAlreadyExistsException e) {
            log.warn("User already exists, prompting login: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(AuthResponse.builder()
                            .userType("ERROR")
                            .accessToken(null)
                            .build());
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Login to Driver App")
    public ResponseEntity<AuthResponse> loginDriver(@RequestParam String firebaseIdToken) {
        log.info("Driver app login request received");

        AuthResponse response = multiRoleLoginService.loginForApp(
                firebaseIdToken,
                MultiRoleAuthService.AppType.DRIVER_APP
        );

        return ResponseEntity.ok(response);
    }

    // Document Upload Endpoints

    @PostMapping("/{driverId}/documents/selfie")
    @Operation(summary = "Upload driver selfie (Step 2a)")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentUploadResponse> uploadSelfie(
            @PathVariable UUID driverId,
            @RequestParam("file") MultipartFile file) {
        log.info("Selfie upload request for driver: {}", driverId);
        DocumentUploadResponse response = driverDocumentService.uploadDriverDocument(
                driverId, DocumentType.SELFIE, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{driverId}/documents/driving-license")
    @Operation(summary = "Upload driving license (Step 2b) - OCR will extract profile info")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentUploadResponse> uploadDrivingLicense(
            @PathVariable UUID driverId,
            @RequestParam("file") MultipartFile file) {
        log.info("Driving license upload request for driver: {}", driverId);
        DocumentUploadResponse response = driverDocumentService.uploadDriverDocument(
                driverId, DocumentType.DRIVING_LICENSE, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{driverId}/documents/revenue-license")
    @Operation(summary = "Upload revenue license (Step 2c)")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentUploadResponse> uploadRevenueLicense(
            @PathVariable UUID driverId,
            @RequestParam("file") MultipartFile file) {
        log.info("Revenue license upload request for driver: {}", driverId);
        DocumentUploadResponse response = driverDocumentService.uploadDriverDocument(
                driverId, DocumentType.REVENUE_LICENSE, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{driverId}/documents/vehicle-registration")
    @Operation(summary = "Upload vehicle registration (Step 2d)")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentUploadResponse> uploadVehicleRegistration(
            @PathVariable UUID driverId,
            @RequestParam("file") MultipartFile file) {
        log.info("Vehicle registration upload request for driver: {}", driverId);
        DocumentUploadResponse response = driverDocumentService.uploadDriverDocument(
                driverId, DocumentType.VEHICLE_REGISTRATION, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{driverId}/documents/vehicle-insurance")
    @Operation(summary = "Upload vehicle insurance (Step 2e)")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentUploadResponse> uploadVehicleInsurance(
            @PathVariable UUID driverId,
            @RequestParam("file") MultipartFile file) {
        log.info("Vehicle insurance upload request for driver: {}", driverId);
        DocumentUploadResponse response = driverDocumentService.uploadDriverDocument(
                driverId, DocumentType.VEHICLE_INSURANCE, file);
        return ResponseEntity.ok(response);
    }

    // Driver Profile and Status Endpoints

    @GetMapping("/{driverId}")
    @Operation(summary = "Get driver profile and verification status")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<DriverResponse> getDriverById(@PathVariable UUID driverId) {
        log.info("Get driver request for ID: {}", driverId);
        DriverResponse response = driverService.getDriverById(driverId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all drivers (Admin/Support)")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<List<DriverResponse>> getAllDrivers() {
        log.info("Get all drivers request");
        List<DriverResponse> response = driverService.getAllDrivers();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending-documents")
    @Operation(summary = "Get drivers who need to upload documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<List<DriverResponse>> getDriversPendingDocuments() {
        log.info("Get drivers pending documents request");
        List<DriverResponse> response = driverService.getDriversPendingDocuments();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending-verification")
    @Operation(summary = "Get drivers pending verification")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<List<DriverResponse>> getPendingVerificationDrivers() {
        log.info("Get pending verification drivers request");
        List<DriverResponse> response = driverService.getPendingVerificationDrivers();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/available")
    @Operation(summary = "Get available drivers")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<List<DriverResponse>> getAvailableDrivers() {
        log.info("Get available drivers request");
        List<DriverResponse> response = driverService.getAvailableDrivers();
        return ResponseEntity.ok(response);
    }

    // Admin/Support Agent Operations

    @PutMapping("/{driverId}/verification")
    @Operation(summary = "Update driver verification status (Manual verification)")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<DriverResponse> updateVerificationStatus(
            @PathVariable UUID driverId,
            @RequestParam boolean isVerified,
            @RequestParam(required = false) String notes) {
        log.info("Manual verification update for driver: {} - {}", driverId, isVerified);
        DriverResponse response = driverService.updateDriverVerificationStatus(driverId, isVerified, notes);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{driverId}/availability")
    @Operation(summary = "Update driver availability (Go online/offline)")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverResponse> updateAvailability(
            @PathVariable UUID driverId,
            @RequestParam boolean isAvailable) {
        log.info("Update driver availability: {} - {}", driverId, isAvailable);
        DriverResponse response = driverService.updateDriverAvailability(driverId, isAvailable);
        return ResponseEntity.ok(response);
    }
}