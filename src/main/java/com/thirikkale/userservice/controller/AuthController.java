package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.request.LoginRequest;
import com.thirikkale.userservice.dto.request.RegisterRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.service.AuthService;
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
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request received for email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for: {}", request.getEmailOrPhone());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}