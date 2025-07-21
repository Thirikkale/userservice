package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.request.DriverProfileSetupRequest;
import com.thirikkale.userservice.dto.request.DriverRegistrationRequest;
import com.thirikkale.userservice.dto.request.VehicleTypeUpdateRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.dto.response.DocumentUploadResponse;
import com.thirikkale.userservice.dto.response.DriverResponse;
import com.thirikkale.userservice.model.enums.DocumentType;
import com.thirikkale.userservice.model.enums.VehicleType;
import com.thirikkale.userservice.service.DriverDocumentService;
import com.thirikkale.userservice.service.DriverService;
import com.thirikkale.userservice.service.MultiRoleAuthService;
import com.thirikkale.userservice.service.MultiRoleLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            summary = "Step 1: Register with Firebase Token",
            description = "Initial registration with Firebase phone authentication token only. " +
                    "Returns token and user ID for profile completion in next step."
    )
    public ResponseEntity<AuthResponse> registerDriver(@Valid @RequestBody DriverRegistrationRequest request) {
        log.info("Driver app token-only registration request received");

        AuthResponse response = multiRoleAuthService.registerUserWithFirebaseOnly(
                request.getFirebaseIdToken(),
                MultiRoleAuthService.AppType.DRIVER_APP
        );

        return ResponseEntity.ok(response);
    }

    //newly added
    // NEW: Vehicle Type Management Endpoints
    @PutMapping("/{driverId}/vehicle-type")
    @Operation(
            summary = "Update vehicle type",
            description = "Update the vehicle type for a driver"
    )

    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverResponse> updateVehicleType(
            @PathVariable UUID driverId,
            @Valid @RequestBody VehicleTypeUpdateRequest request) {
        log.info("Vehicle type update request for driver: {} to {}", driverId, request.getVehicleType());

        DriverResponse response = driverService.updateDriverVehicleType(driverId, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle-types")
    @Operation(summary = "Get available vehicle types")
    public ResponseEntity<VehicleType[]> getVehicleTypes() {
        log.info("Get vehicle types request received");
        return ResponseEntity.ok(VehicleType.values());
    }


    @PutMapping("/{driverId}/complete-profile")
    @Operation(
            summary = "Step 2: Complete Profile Setup",
            description = "Complete driver profile with first name, last name, and optional WhatsApp number after token registration."
    )
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<AuthResponse> completeDriverProfile(
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverProfileSetupRequest request) {
        log.info("Completing driver profile setup for: {}", driverId);

        AuthResponse response = multiRoleAuthService.completeProfileSetup(
                driverId,
                request.getFirstName(),
                request.getLastName(),
                request.getWhatsappNumber(),
                MultiRoleAuthService.AppType.DRIVER_APP
        );

        return ResponseEntity.ok(response);
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
    @Operation(summary = "Upload driver selfie (Step 3a)")
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
    @Operation(summary = "Upload driving license (Step 3b) - OCR will extract profile info")
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
    @Operation(summary = "Upload revenue license (Step 3c)")
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
    @Operation(summary = "Upload vehicle registration (Step 3d)")
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
    @Operation(summary = "Upload vehicle insurance (Step 3e)")
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

    @GetMapping("/{driverId}/processing-status")
    @Operation(summary = "Get driver document processing status")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverResponse> getProcessingStatus(@PathVariable UUID driverId) {
        log.info("Get processing status for driver: {}", driverId);
        DriverResponse response = driverService.getDriverById(driverId);
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

    // Admin/Support Agent Operations

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
}