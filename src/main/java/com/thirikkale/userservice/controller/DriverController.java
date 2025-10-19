package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.request.DriverProfileSetupRequest;
import com.thirikkale.userservice.dto.request.DriverProfileUpdateRequest;
import com.thirikkale.userservice.dto.request.DriverRegistrationRequest;
import com.thirikkale.userservice.dto.request.VehicleRegistrationRequest;
import com.thirikkale.userservice.dto.request.VehicleTypeUpdateRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.dto.response.DocumentUploadResponse;
import com.thirikkale.userservice.dto.response.DriverResponse;
import com.thirikkale.userservice.dto.response.VehicleResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Driver;
import com.thirikkale.userservice.model.enums.DocumentType;
import com.thirikkale.userservice.model.enums.VehicleType;
import com.thirikkale.userservice.repository.DriverRepository;
import com.thirikkale.userservice.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Driver Management", description = "Driver registration, authentication, document management and operations")
public class DriverController {

    private final DriverService driverService;
    private final VehicleService vehicleService;
    private final DriverDocumentService driverDocumentService;
    private final MultiRoleAuthService multiRoleAuthService;
    private final MultiRoleLoginService multiRoleLoginService;
    private final FileStorageService fileStorageService;
    private final DriverRepository driverRepository;

    @PostMapping("/register")
    @Operation(summary = "Step 1: Register with Firebase Token", description = "Initial registration with Firebase phone authentication token only. "
            +
            "Returns token and user ID for profile completion in next step.")
    public ResponseEntity<AuthResponse> registerDriver(@Valid @RequestBody DriverRegistrationRequest request) {
        log.info("Driver app token-only registration request received");

        AuthResponse response = multiRoleAuthService.registerUserWithFirebaseOnly(
                request.getFirebaseIdToken(),
                MultiRoleAuthService.AppType.DRIVER_APP);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{driverId}/complete-profile")
    @Operation(summary = "Step 2: Complete Profile Setup", description = "Complete driver profile with first name, last name, and optional WhatsApp number after token registration.")
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
                MultiRoleAuthService.AppType.DRIVER_APP);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login to Driver App")
    public ResponseEntity<AuthResponse> loginDriver(@RequestParam String firebaseIdToken) {
        log.info("Driver app login request received");

        AuthResponse response = multiRoleLoginService.loginForApp(
                firebaseIdToken,
                MultiRoleAuthService.AppType.DRIVER_APP);

        return ResponseEntity.ok(response);
    }

    // Vehicle Management Endpoints
    @PostMapping("/{driverId}/vehicles")
    @Operation(summary = "Register a new vehicle")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<VehicleResponse> registerVehicle(
            @PathVariable UUID driverId,
            @Valid @RequestBody VehicleRegistrationRequest request) {
        log.info("Vehicle registration request for driver: {}", driverId);
        VehicleResponse response = vehicleService.registerVehicle(driverId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{driverId}/vehicles")
    @Operation(summary = "Get all vehicles for a driver")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<List<VehicleResponse>> getDriverVehicles(@PathVariable UUID driverId) {
        log.info("Get vehicles request for driver: {}", driverId);
        List<VehicleResponse> response = vehicleService.getDriverVehicles(driverId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{driverId}/vehicles/{vehicleId}")
    @Operation(summary = "Get specific vehicle details")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<VehicleResponse> getVehicleById(
            @PathVariable UUID driverId,
            @PathVariable UUID vehicleId) {
        log.info("Get vehicle {} for driver: {}", vehicleId, driverId);
        VehicleResponse response = vehicleService.getVehicleById(driverId, vehicleId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{driverId}/vehicles/{vehicleId}/set-primary")
    @Operation(summary = "Set vehicle as primary")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<VehicleResponse> setPrimaryVehicle(
            @PathVariable UUID driverId,
            @PathVariable UUID vehicleId) {
        log.info("Set primary vehicle {} for driver: {}", vehicleId, driverId);
        VehicleResponse response = vehicleService.setPrimaryVehicle(driverId, vehicleId);
        return ResponseEntity.ok(response);
    }

    // Personal Document Upload Endpoints
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

    // Vehicle Document Upload Endpoints
    @PostMapping("/{driverId}/vehicles/{vehicleId}/documents/revenue-license")
    @Operation(summary = "Upload revenue license for specific vehicle")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentUploadResponse> uploadRevenueLicense(
            @PathVariable UUID driverId,
            @PathVariable UUID vehicleId,
            @RequestParam("file") MultipartFile file) {
        log.info("Revenue license upload for vehicle {} of driver: {}", vehicleId, driverId);
        DocumentUploadResponse response = driverDocumentService.uploadVehicleDocument(
                driverId, vehicleId, DocumentType.REVENUE_LICENSE, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{driverId}/vehicles/{vehicleId}/documents/vehicle-registration")
    @Operation(summary = "Upload vehicle registration document")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentUploadResponse> uploadVehicleRegistration(
            @PathVariable UUID driverId,
            @PathVariable UUID vehicleId,
            @RequestParam("file") MultipartFile file) {
        log.info("Vehicle registration upload for vehicle {} of driver: {}", vehicleId, driverId);
        DocumentUploadResponse response = driverDocumentService.uploadVehicleDocument(
                driverId, vehicleId, DocumentType.VEHICLE_REGISTRATION, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{driverId}/vehicles/{vehicleId}/documents/vehicle-insurance")
    @Operation(summary = "Upload vehicle insurance document")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentUploadResponse> uploadVehicleInsurance(
            @PathVariable UUID driverId,
            @PathVariable UUID vehicleId,
            @RequestParam("file") MultipartFile file) {
        log.info("Vehicle insurance upload for vehicle {} of driver: {}", vehicleId, driverId);
        DocumentUploadResponse response = driverDocumentService.uploadVehicleDocument(
                driverId, vehicleId, DocumentType.VEHICLE_INSURANCE, file);
        return ResponseEntity.ok(response);
    }

    // Driver Profile and Status Endpoints
    @GetMapping("/{driverId}")
    @Operation(summary = "Get driver by ID")
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
        log.info("Processing status request for driver: {}", driverId);
        DriverResponse response = driverService.getDriverById(driverId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{driverId}/availability")
    @Operation(summary = "Update driver availability (Go online/offline)")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverResponse> updateAvailability(
            @PathVariable UUID driverId,
            @RequestParam boolean isAvailable) {
        log.info("Update availability request for driver: {} - {}", driverId, isAvailable);
        DriverResponse response = driverService.updateDriverAvailability(driverId, isAvailable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{driverId}/profile")
    @Operation(summary = "Update driver profile", description = "Update driver profile information like name, date of birth, etc.")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverResponse> updateDriverProfile(
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverProfileUpdateRequest request) {
        log.info("Update driver profile request for: {}", driverId);
        DriverResponse response = driverService.updateDriverProfile(driverId, request);
        return ResponseEntity.ok(response);
    }

    // Admin/Support Agent Operations
    @GetMapping
    @Operation(summary = "Get all drivers (Admin/Support)")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<List<DriverResponse>> getAllDrivers() {
        log.info("Get all drivers request received");
        List<DriverResponse> response = driverService.getAllDrivers();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/available")
    @Operation(summary = "Get available drivers")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<List<DriverResponse>> getAvailableDrivers() {
        log.info("Get available drivers request received");
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
        log.info("Update verification status request for driver: {} - {}", driverId, isVerified);
        DriverResponse response = driverService.updateDriverVerificationStatus(driverId, isVerified, notes);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{driverId}/documents/{documentType}/status")
    @Operation(summary = "Update individual document verification status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<DriverResponse> updateDocumentVerificationStatus(
            @PathVariable UUID driverId,
            @PathVariable String documentType,
            @RequestParam String status) {
        log.info("Update document verification status for driver: {}, documentType: {}, status: {}",
                driverId, documentType, status);
        DriverResponse response = driverService.updateDocumentVerificationStatus(driverId, documentType, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{driverId}/documents/status")
    @Operation(summary = "Get document status and verification progress")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(@PathVariable UUID driverId) {
        log.info("Document status request for driver: {}", driverId);

        // Use service method instead of calling entity methods directly
        Map<String, Object> status = driverService.getDriverDocumentStatus(driverId);
        return ResponseEntity.ok(status);
    }

    private Map<String, Object> createDocumentStatus(String type, String url) {
        Map<String, Object> status = new HashMap<>();
        status.put("type", type);
        status.put("uploaded", url != null);
        status.put("url", url);
        return status;
    }

    @GetMapping("/vehicle-types")
    @Operation(summary = "Get available vehicle types")
    public ResponseEntity<List<VehicleType>> getVehicleTypes() {
        List<VehicleType> vehicleTypes = Arrays.asList(VehicleType.values());
        return ResponseEntity.ok(vehicleTypes);
    }

    // Add this endpoint after the vehicle management endpoints
    @PutMapping("/{driverId}/vehicles/{vehicleId}/vehicle-type")
    @Operation(summary = "Update vehicle type for specific vehicle")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<VehicleResponse> updateVehicleType(
            @PathVariable UUID driverId,
            @PathVariable UUID vehicleId,
            @Valid @RequestBody VehicleTypeUpdateRequest request) {
        log.info("Update vehicle type request for vehicle {} of driver: {} - new type: {}",
                vehicleId, driverId, request.getVehicleType());
        VehicleResponse response = vehicleService.updateVehicleType(driverId, vehicleId, request);
        return ResponseEntity.ok(response);
    }

    // Alternative: Quick vehicle type update for primary vehicle
    @PutMapping("/{driverId}/primary-vehicle/vehicle-type")
    @Operation(summary = "Update vehicle type for primary vehicle")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<VehicleResponse> updatePrimaryVehicleType(
            @PathVariable UUID driverId,
            @Valid @RequestBody VehicleTypeUpdateRequest request) {
        log.info("Update primary vehicle type request for driver: {} - new type: {}",
                driverId, request.getVehicleType());
        VehicleResponse response = vehicleService.updatePrimaryVehicleType(driverId, request);
        return ResponseEntity.ok(response);
    }

}