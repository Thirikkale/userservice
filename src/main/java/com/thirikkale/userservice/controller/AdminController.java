package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.request.AdminLoginRequest;
import com.thirikkale.userservice.dto.request.AdminRegistrationRequest;
import com.thirikkale.userservice.dto.request.SuperAdminRegistrationRequest;
import com.thirikkale.userservice.dto.response.AdminRegistrationResponse;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Authentication", description = "Admin authentication and management operations")
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/register-super-admin")
    @Operation(
            summary = "Register super admin (First-time setup only)",
            description = "Creates the first super admin account. Can only be used when no admin exists."
    )
    public ResponseEntity<AdminRegistrationResponse> registerSuperAdmin(@Valid @RequestBody SuperAdminRegistrationRequest request) {
        log.info("Super admin registration request received for email: {}", request.getEmail());
        AdminRegistrationResponse response = adminService.registerSuperAdmin(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register admin user",
            description = "Register new admin user (Admin, Rider Support Agent, Driver Support Agent). Only existing admins can create new admin users."
    )
    @PreAuthorize("hasRole('ADMIN_ADMIN')")
    public ResponseEntity<AdminRegistrationResponse> registerAdmin(@Valid @RequestBody AdminRegistrationRequest request) {
        log.info("Admin registration request received for email: {} with role: {}", request.getEmail(), request.getAdminRole());
        AdminRegistrationResponse response = adminService.registerAdmin(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Admin login")
    public ResponseEntity<AuthResponse> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.info("Admin login request received for: {}", request.getEmailOrPhone());
        AuthResponse response = adminService.adminLogin(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    @Operation(summary = "Get all admin users")
    @PreAuthorize("hasRole('ADMIN_ADMIN')")
    public ResponseEntity<List<AdminRegistrationResponse>> getAllAdmins() {
        log.info("Get all admin users request received");
        List<AdminRegistrationResponse> response = adminService.getAllAdmins();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/profile/{adminId}")
    @Operation(summary = "Get admin profile")
    @PreAuthorize("hasAnyRole('ADMIN_ADMIN', 'ADMIN_RIDER_SUPPORT_AGENT', 'ADMIN_DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<AuthResponse> getAdminProfile(@PathVariable UUID adminId) {
        log.info("Get admin profile request for: {}", adminId);
        AuthResponse response = adminService.getAdminProfile(adminId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{adminId}/deactivate")
    @Operation(summary = "Deactivate admin user")
    @PreAuthorize("hasRole('ADMIN_ADMIN')")
    public ResponseEntity<AdminRegistrationResponse> deactivateAdmin(@PathVariable UUID adminId) {
        log.info("Deactivate admin request received for ID: {}", adminId);
        AdminRegistrationResponse response = adminService.deactivateAdmin(adminId);
        return ResponseEntity.ok(response);
    }
}