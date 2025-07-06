package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.request.RiderRegistrationRequest;
import com.thirikkale.userservice.dto.request.RiderProfileUpdateRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.dto.response.GenderDetectionResponse;
import com.thirikkale.userservice.dto.response.RiderResponse;
import com.thirikkale.userservice.service.GenderDetectionService;
import com.thirikkale.userservice.service.RiderService;
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
@RequestMapping("/api/v1/riders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rider Management", description = "Rider registration and management operations")
public class RiderController {

    private final RiderService riderService;
    private final GenderDetectionService genderDetectionService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new rider (Simplified)",
            description = "Register a new rider with minimal required information. " +
                    "Only Firebase token, first name, and last name are required. " +
                    "Other details can be updated later via profile update endpoint."
    )
    public ResponseEntity<AuthResponse> registerRider(@Valid @RequestBody RiderRegistrationRequest request) {
        log.info("Simplified rider registration request received");
        AuthResponse response = riderService.registerRider(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{riderId}/profile")
    @Operation(summary = "Update rider profile")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<RiderResponse> updateRiderProfile(
            @PathVariable UUID riderId,
            @Valid @RequestBody RiderProfileUpdateRequest request) {
        log.info("Update rider profile request for: {}", riderId);
        RiderResponse response = riderService.updateRiderProfile(riderId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{riderId}/profile-photo")
    @Operation(summary = "Upload profile photo")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<RiderResponse> uploadProfilePhoto(
            @PathVariable UUID riderId,
            @RequestParam("photo") MultipartFile photoFile) {
        log.info("Profile photo upload request for rider: {}", riderId);
        // This would be implemented in FileStorageService
        // For now, return current profile
        RiderResponse response = riderService.getRiderById(riderId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{riderId}/gender-detection")
    @Operation(
            summary = "Upload selfie for gender detection (Optional)",
            description = "Optional step - rider can upload selfie for gender detection to enable women-only rides feature"
    )
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<GenderDetectionResponse> uploadSelfieForGenderDetection(
            @PathVariable("riderId") UUID riderId,
            @RequestParam("selfie") MultipartFile selfieFile) {
        log.info("Optional gender detection request for rider: {}", riderId);
        GenderDetectionResponse response = genderDetectionService.detectGenderFromSelfie(riderId, selfieFile);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{riderId}/skip-gender-detection")
    @Operation(
            summary = "Skip gender detection (Optional)",
            description = "Rider can skip gender detection if they don't want to use women-only rides feature"
    )
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<Void> skipGenderDetection(@PathVariable("riderId") UUID riderId) {
        log.info("Skip gender detection request for rider: {}", riderId);
        genderDetectionService.skipGenderDetection(riderId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{riderId}")
    @Operation(summary = "Get rider by ID")
    @PreAuthorize("hasAnyRole('RIDER', 'ADMIN', 'RIDER_SUPPORT_AGENT')")
    public ResponseEntity<RiderResponse> getRiderById(@PathVariable UUID riderId) {
        log.info("Get rider request for ID: {}", riderId);
        RiderResponse response = riderService.getRiderById(riderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all riders")
    @PreAuthorize("hasAnyRole('ADMIN', 'RIDER_SUPPORT_AGENT')")
    public ResponseEntity<List<RiderResponse>> getAllRiders() {
        log.info("Get all riders request");
        List<RiderResponse> response = riderService.getAllRiders();
        return ResponseEntity.ok(response);
    }
}