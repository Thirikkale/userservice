package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.request.RiderProfileSetupRequest;
import com.thirikkale.userservice.dto.request.RiderRegistrationRequest;
import com.thirikkale.userservice.dto.request.RiderProfileUpdateRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.dto.response.GenderDetectionResponse;
import com.thirikkale.userservice.dto.response.RiderResponse;
import com.thirikkale.userservice.service.GenderDetectionService;
import com.thirikkale.userservice.service.RiderService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/riders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rider Management", description = "Rider registration, authentication and management operations")
public class RiderController {

    private final RiderService riderService;
    private final GenderDetectionService genderDetectionService;
    private final MultiRoleAuthService multiRoleAuthService;
    private final MultiRoleLoginService multiRoleLoginService;

    @PostMapping("/register")
    @Operation(
            summary = "Step 1: Register with Firebase Token",
            description = "Initial registration with Firebase phone authentication token only. " +
                    "Returns token and user ID for profile completion in next step."
    )
    public ResponseEntity<AuthResponse> registerRider(@Valid @RequestBody RiderRegistrationRequest request) {
        log.info("Rider app token-only registration request received");

        AuthResponse response = multiRoleAuthService.registerUserWithFirebaseOnly(
                request.getFirebaseIdToken(),
                MultiRoleAuthService.AppType.RIDER_APP
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{riderId}/complete-profile")
    @Operation(
            summary = "Step 2: Complete Profile Setup",
            description = "Complete rider profile with first name and last name after token registration."
    )
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<AuthResponse> completeRiderProfile(
            @PathVariable UUID riderId,
            @Valid @RequestBody RiderProfileSetupRequest request) {
        log.info("Completing rider profile setup for: {}", riderId);

        AuthResponse response = multiRoleAuthService.completeProfileSetup(
                riderId,
                request.getFirstName(),
                request.getLastName(),
                null, // No whatsapp for riders
                MultiRoleAuthService.AppType.RIDER_APP
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login to Rider App")
    public ResponseEntity<AuthResponse> loginRider(@RequestParam String firebaseIdToken) {
        log.info("Rider app login request received");

        AuthResponse response = multiRoleLoginService.loginForApp(
                firebaseIdToken,
                MultiRoleAuthService.AppType.RIDER_APP
        );

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

    @GetMapping("/{riderId}/women-only-status")
    @Operation(
            summary = "Check women-only rides access status",
            description = "Check if rider has access to women-only rides feature"
    )
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<Map<String, Object>> getWomenOnlyStatus(@PathVariable UUID riderId) {
        log.info("Check women-only status for rider: {}", riderId);

        RiderResponse rider = riderService.getRiderById(riderId);

        Map<String, Object> status = new HashMap<>();
        status.put("riderId", riderId);
        status.put("genderVerified", rider.getGenderVerified());
        status.put("gender", rider.getGender());
        status.put("womenOnlyAccess", rider.getWomenOnlyAccess());
        status.put("hasProfilePhoto", rider.getProfilePhotoUrl() != null);

        return ResponseEntity.ok(status);
    }

    @GetMapping("/{riderId}")
    @Operation(summary = "Get rider by ID")
    @PreAuthorize("hasAnyRole('RIDER', 'ADMIN', 'RIDER_SUPPORT_AGENT', 'SERVICE')")
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