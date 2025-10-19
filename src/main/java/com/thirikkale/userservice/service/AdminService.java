package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.request.AdminLoginRequest;
import com.thirikkale.userservice.dto.request.AdminRegistrationRequest;
import com.thirikkale.userservice.dto.request.SuperAdminRegistrationRequest;
import com.thirikkale.userservice.dto.response.AdminRegistrationResponse;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Admin;
import com.thirikkale.userservice.model.enums.AdminRoleType;
import com.thirikkale.userservice.model.enums.AdminStatus;
import com.thirikkale.userservice.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin Service using separate Admin table
 * Admins are system actors, completely separate from Users (Drivers & Riders)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    /**
     * Register super admin (first admin only)
     */
    @Transactional
    public AdminRegistrationResponse registerSuperAdmin(SuperAdminRegistrationRequest request) {
        log.info("Registering super admin with email: {}", request.getEmail());

        // Check if any admin already exists (super admin should be first)
        long adminCount = adminRepository.countActiveAdmins();
        if (adminCount > 0) {
            throw new CustomExceptions.UserAlreadyExistsException(
                    "Super admin already exists. Use admin registration instead.");
        }

        // Convert to AdminRegistrationRequest and force ADMIN role
        AdminRegistrationRequest adminRequest = new AdminRegistrationRequest();
        adminRequest.setEmail(request.getEmail());
        adminRequest.setPassword(request.getPassword());
        adminRequest.setFirstName(request.getFirstName());
        adminRequest.setLastName(request.getLastName());
        adminRequest.setPhoneNumber(request.getPhoneNumber());
        adminRequest.setAdminRole(AdminRoleType.ADMIN); // Force to ADMIN role for super admin

        return registerAdmin(adminRequest);
    }

    /**
     * Register a new admin
     */
    @Transactional
    public AdminRegistrationResponse registerAdmin(AdminRegistrationRequest request) {
        log.info("Registering admin user with email: {} and role: {}", request.getEmail(), request.getAdminRole());

        // Check if admin with email already exists
        if (adminRepository.existsByEmail(request.getEmail())) {
            throw new CustomExceptions.UserAlreadyExistsException("Admin with this email already exists");
        }

        // Check if admin with phone number already exists
        if (adminRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new CustomExceptions.UserAlreadyExistsException("Admin with this phone number already exists");
        }

        // Generate email verification token
        String verificationToken = UUID.randomUUID().toString();
        LocalDateTime tokenExpiry = LocalDateTime.now().plusHours(24); // Token valid for 24 hours

        // Create new admin
        Admin admin = Admin.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .adminRole(request.getAdminRole())
                .status(AdminStatus.PENDING_ACTIVATION) // New admins need activation
                .emailVerificationToken(verificationToken)
                .emailVerificationTokenExpiry(tokenExpiry)
                .emailVerified(false)
                .passwordChangedAt(LocalDateTime.now())
                .build();

        // Save admin
        admin = adminRepository.save(admin);
        log.info("Admin created successfully with ID: {}", admin.getAdminId());

        // Send verification email
        try {
            emailService.sendAdminVerificationEmail(
                    admin.getEmail(),
                    admin.getFirstName(),
                    verificationToken);
            log.info("Verification email sent to: {}", admin.getEmail());
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", admin.getEmail(), e);
            // Don't fail registration if email fails, admin can request resend
        }

        // Build response
        return AdminRegistrationResponse.builder()
                .success(true)
                .message("Admin user registered successfully. Please check your email to verify your account.")
                .adminId(admin.getAdminId())
                .email(admin.getEmail())
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .adminRole(admin.getAdminRole())
                .createdAt(admin.getCreatedAt())
                .build();
    }

    /**
     * Admin login
     */
    @Transactional
    public AuthResponse adminLogin(AdminLoginRequest request) {
        log.info("Admin login attempt for: {}", request.getEmailOrPhone());

        // Find admin by email or phone
        Admin admin = adminRepository.findByEmailOrPhoneNumber(request.getEmailOrPhone())
                .orElseThrow(() -> new CustomExceptions.InvalidCredentialsException("Invalid credentials"));

        // Check account status
        if (admin.getStatus() == AdminStatus.PENDING_ACTIVATION) {
            throw new CustomExceptions.AccountDeactivatedException(
                    "Account is pending activation. Please contact system administrator.");
        }

        if (admin.getStatus() == AdminStatus.SUSPENDED) {
            throw new CustomExceptions.AccountDeactivatedException(
                    "Account is suspended. Please contact system administrator.");
        }

        if (admin.getStatus() == AdminStatus.DEACTIVATED) {
            throw new CustomExceptions.AccountDeactivatedException(
                    "Account is deactivated. Please contact system administrator.");
        }

        if (admin.getStatus() == AdminStatus.OFFLINE) {
            // Allow login but will change status to ONLINE
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new CustomExceptions.InvalidCredentialsException("Invalid credentials");
        }

        // Successful login - update status to ONLINE and last login
        admin.setStatus(AdminStatus.ONLINE);
        admin.setLastLogin(LocalDateTime.now());
        adminRepository.save(admin);

        // Generate tokens
        String userType = "ADMIN_" + admin.getAdminRole().name();
        String accessToken = jwtService.generateAccessToken(admin.getAdminId(), admin.getPhoneNumber(), userType);
        String refreshToken = jwtService.generateRefreshToken(admin.getAdminId(), admin.getPhoneNumber());

        log.info("Admin login successful: {} - {}", admin.getEmail(), admin.getAdminRole());

        return AuthResponse.builder()
                .userId(admin.getAdminId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userType(userType)
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .phoneNumber(admin.getPhoneNumber())
                .email(admin.getEmail())
                .isVerified(true)
                .loginTime(LocalDateTime.now())
                .build();
    }

    /**
     * Get all admins
     */
    public List<AdminRegistrationResponse> getAllAdmins() {
        log.info("Getting all admin users");

        return adminRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get admin by ID
     */
    public AuthResponse getAdminProfile(UUID adminId) {
        log.info("Getting admin profile: {}", adminId);

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Admin not found"));

        String userType = "ADMIN_" + admin.getAdminRole().name();

        return AuthResponse.builder()
                .userId(admin.getAdminId())
                .userType(userType)
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .phoneNumber(admin.getPhoneNumber())
                .email(admin.getEmail())
                .isVerified(true)
                .loginTime(admin.getLastLogin())
                .build();
    }

    /**
     * Deactivate admin
     */
    @Transactional
    public AdminRegistrationResponse deactivateAdmin(UUID adminId) {
        log.info("Deactivating admin: {}", adminId);

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Admin not found"));

        admin.setStatus(AdminStatus.DEACTIVATED);
        admin.setDeactivatedAt(LocalDateTime.now());

        admin = adminRepository.save(admin);

        log.info("Admin deactivated: {}", adminId);

        return mapToResponse(admin);
    }

    /**
     * Activate admin
     */
    @Transactional
    public AdminRegistrationResponse activateAdmin(UUID adminId) {
        log.info("Activating admin with ID: {}", adminId);

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Admin not found"));

        admin.setStatus(AdminStatus.ACTIVATED);
        admin.setActivatedAt(LocalDateTime.now());
        admin.setDeactivatedAt(null);

        admin = adminRepository.save(admin);

        return mapToResponse(admin);
    }

    /**
     * Suspend admin
     */
    @Transactional
    public AdminRegistrationResponse suspendAdmin(UUID adminId) {
        log.info("Suspending admin with ID: {}", adminId);

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Admin not found"));

        admin.setStatus(AdminStatus.SUSPENDED);
        admin.setSuspendedAt(LocalDateTime.now());

        admin = adminRepository.save(admin);

        return mapToResponse(admin);
    }

    /**
     * Set admin offline
     */
    @Transactional
    public void setAdminOffline(UUID adminId) {
        log.info("Setting admin offline: {}", adminId);

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Admin not found"));

        admin.setStatus(AdminStatus.OFFLINE);
        adminRepository.save(admin);
    }

    /**
     * Get admins by status
     */
    public List<AdminRegistrationResponse> getAdminsByStatus(AdminStatus status) {
        log.info("Fetching admins with status: {}", status);

        return adminRepository.findByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get active admins count
     */
    public long getActiveAdminsCount() {
        return adminRepository.countActiveAdmins();
    }

    /**
     * Verify admin email and activate account
     */
    @Transactional
    public AdminRegistrationResponse verifyEmail(String token) {
        log.info("Verifying email with token");

        // Find admin by verification token
        Admin admin = adminRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new CustomExceptions.InvalidCredentialsException(
                        "Invalid or expired verification token"));

        // Check if token is expired
        if (admin.getEmailVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new CustomExceptions.InvalidCredentialsException(
                    "Verification token has expired. Please request a new one.");
        }

        // Check if already verified
        if (admin.getEmailVerified()) {
            throw new CustomExceptions.UserAlreadyExistsException("Email already verified");
        }

        // Update admin status
        admin.setEmailVerified(true);
        admin.setStatus(AdminStatus.ACTIVATED);
        admin.setActivatedAt(LocalDateTime.now());
        admin.setEmailVerificationToken(null); // Clear token after verification
        admin.setEmailVerificationTokenExpiry(null);

        admin = adminRepository.save(admin);
        log.info("Email verified successfully for admin: {}", admin.getEmail());

        return AdminRegistrationResponse.builder()
                .success(true)
                .message("Email verified successfully! Your account is now activated and you can log in.")
                .adminId(admin.getAdminId())
                .email(admin.getEmail())
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .adminRole(admin.getAdminRole())
                .createdAt(admin.getCreatedAt())
                .build();
    }

    /**
     * Resend verification email
     */
    @Transactional
    public AdminRegistrationResponse resendVerificationEmail(String email) {
        log.info("Resending verification email for: {}", email);

        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Admin not found"));

        // Check if already verified
        if (admin.getEmailVerified()) {
            throw new CustomExceptions.UserAlreadyExistsException("Email already verified");
        }

        // Generate new token
        String verificationToken = UUID.randomUUID().toString();
        LocalDateTime tokenExpiry = LocalDateTime.now().plusHours(24);

        admin.setEmailVerificationToken(verificationToken);
        admin.setEmailVerificationTokenExpiry(tokenExpiry);
        admin = adminRepository.save(admin);

        // Send verification email
        try {
            emailService.sendAdminVerificationEmail(
                    admin.getEmail(),
                    admin.getFirstName(),
                    verificationToken);
            log.info("Verification email resent to: {}", admin.getEmail());
        } catch (Exception e) {
            log.error("Failed to resend verification email to: {}", admin.getEmail(), e);
            throw new RuntimeException("Failed to send verification email", e);
        }

        return AdminRegistrationResponse.builder()
                .success(true)
                .message("Verification email sent successfully. Please check your email.")
                .adminId(admin.getAdminId())
                .email(admin.getEmail())
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .adminRole(admin.getAdminRole())
                .createdAt(admin.getCreatedAt())
                .build();
    }

    /**
     * Map Admin entity to AdminRegistrationResponse
     */
    private AdminRegistrationResponse mapToResponse(Admin admin) {
        return AdminRegistrationResponse.builder()
                .success(true)
                .message("Admin user found")
                .adminId(admin.getAdminId())
                .email(admin.getEmail())
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .phoneNumber(admin.getPhoneNumber())
                .adminRole(admin.getAdminRole())
                .status(admin.getStatus())
                .emailVerified(admin.getEmailVerified())
                .lastLoginAt(admin.getLastLogin())
                .createdAt(admin.getCreatedAt())
                .build();
    }
}