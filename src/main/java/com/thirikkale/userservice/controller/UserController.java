package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.response.UserResponse;
import com.thirikkale.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "User management operations")
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT', 'RIDER_SUPPORT_AGENT')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        log.info("Get user request received for ID: {}", userId);
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserResponse> getCurrentUserProfile(Authentication authentication) {
        String phoneNumber = authentication.getName();
        log.info("Get profile request received for phone: {}", phoneNumber);
        UserResponse response = userService.getUserByPhoneNumber(phoneNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        log.info("Get all users request received");
        List<UserResponse> response = userService.getAllUsers();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/deactivate")
    @Operation(summary = "Deactivate user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID userId) {
        log.info("Deactivate user request received for ID: {}", userId);
        UserResponse response = userService.deactivateUser(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/activate")
    @Operation(summary = "Activate user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> activateUser(@PathVariable UUID userId) {
        log.info("Activate user request received for ID: {}", userId);
        UserResponse response = userService.activateUser(userId);
        return ResponseEntity.ok(response);
    }
}