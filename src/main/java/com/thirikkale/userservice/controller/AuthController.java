package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.request.LoginRequest;
import com.thirikkale.userservice.dto.request.RegisterRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication operations")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register new user (for admin/support agents)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request received for email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login/password")
    @Operation(summary = "Login with email/phone and password (for admin/support agents)")
    public ResponseEntity<AuthResponse> loginWithPassword(@Valid @RequestBody LoginRequest request) {
        log.info("Password login request received for: {}", request.getEmailOrPhone());
        AuthResponse response = authService.loginWithPassword(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/firebase")
    @Operation(summary = "Login existing user with Firebase token")
    public ResponseEntity<AuthResponse> loginWithFirebase(@RequestParam String firebaseIdToken) {
        log.info("Firebase login request received");
        AuthResponse response = authService.loginWithFirebase(firebaseIdToken);
        return ResponseEntity.ok(response);
    }

    // Backward compatibility
    @PostMapping("/login")
    @Operation(summary = "Legacy login endpoint")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Legacy login request received for: {}", request.getEmailOrPhone());
        AuthResponse response = authService.loginWithPassword(request);
        return ResponseEntity.ok(response);
    }
}