package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.request.AdminLoginRequest;
import com.thirikkale.userservice.dto.request.AdminRegistrationRequest;
import com.thirikkale.userservice.dto.request.SuperAdminRegistrationRequest;
import com.thirikkale.userservice.dto.response.AdminRegistrationResponse;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.AdminRole;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.model.enums.AdminRoleType;
import com.thirikkale.userservice.repository.AdminRoleRepository;
import com.thirikkale.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AdminRegistrationResponse registerSuperAdmin(SuperAdminRegistrationRequest request) {
        log.info("Registering super admin with email: {}", request.getEmail());

        // Check if any admin already exists (super admin should be first)
        long adminCount = adminRoleRepository.countActiveAdmins();
        if (adminCount > 0) {
            throw new CustomExceptions.UserAlreadyExistsException("Super admin already exists. Use admin registration instead.");
        }

        // Convert to AdminRegistrationRequest and set role
        AdminRegistrationRequest adminRequest = new AdminRegistrationRequest();
        adminRequest.setEmail(request.getEmail());
        adminRequest.setPassword(request.getPassword());
        adminRequest.setFirstName(request.getFirstName());
        adminRequest.setLastName(request.getLastName());
        adminRequest.setPhoneNumber(request.getPhoneNumber());
        adminRequest.setAdminRole(AdminRoleType.ADMIN); // Force to ADMIN role

        return createAdminUser(adminRequest);
    }

    @Transactional
    public AdminRegistrationResponse registerAdmin(AdminRegistrationRequest request) {
        log.info("Registering admin user with email: {} and role: {}", request.getEmail(), request.getAdminRole());

        return createAdminUser(request);
    }

    private AdminRegistrationResponse createAdminUser(AdminRegistrationRequest request) {
        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomExceptions.UserAlreadyExistsException("User with email already exists");
        }

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new CustomExceptions.UserAlreadyExistsException("User with phone number already exists");
        }

        // Create and save User first
        User user = User.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .isActive(true)
                .isEmailVerified(true) // Admin accounts are pre-verified
                .isPhoneVerified(true)
                .lastLoginAt(null) // Will be set on first login
                .build();

        // Save user first to get the generated ID
        user = userRepository.save(user);
        log.info("User created with ID: {}", user.getUserId());

        // Create Admin Role with the saved user's ID
        AdminRole adminRole = AdminRole.builder()
                .adminId(user.getUserId()) // Use the generated user ID
                .user(user) // Reference to the saved user
                .adminRole(request.getAdminRole())
                .lastLogin(null)
                .build();

        // Save the AdminRole
        try {
            adminRoleRepository.save(adminRole);
            log.info("Admin role created successfully for user: {}", user.getUserId());
        } catch (Exception e) {
            log.error("Failed to create admin role for user: {}", user.getUserId(), e);
            // If admin role creation fails, we should rollback the user creation
            throw new RuntimeException("Failed to create admin role", e);
        }

        log.info("Admin user created successfully: {} with role: {}", user.getUserId(), request.getAdminRole());

        return AdminRegistrationResponse.builder()
                .success(true)
                .message("Admin user registered successfully")
                .adminId(user.getUserId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .adminRole(request.getAdminRole())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Transactional
    public AuthResponse adminLogin(AdminLoginRequest request) {
        log.info("Admin login attempt for: {}", request.getEmailOrPhone());

        // Find user by email or phone
        User user = userRepository.findByEmailOrPhoneNumber(request.getEmailOrPhone())
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found"));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new CustomExceptions.UserNotActiveException("User account is deactivated");
        }

        // Verify password
        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomExceptions.InvalidCredentialsException("Invalid credentials");
        }

        // Check if user has admin role
        AdminRole adminRole = adminRoleRepository.findByIdWithUser(user.getUserId())
                .orElseThrow(() -> new CustomExceptions.InvalidCredentialsException("Access denied: Admin privileges required"));

        // Update last login - fetch fresh entities to avoid stale state
        User freshUser = userRepository.findById(user.getUserId()).orElseThrow();
        AdminRole freshAdminRole = adminRoleRepository.findById(adminRole.getAdminId()).orElseThrow();

        freshUser.setLastLoginAt(LocalDateTime.now());
        freshAdminRole.setLastLogin(LocalDateTime.now());

        userRepository.save(freshUser);
        adminRoleRepository.save(freshAdminRole);

        // Generate tokens
        String userType = "ADMIN_" + adminRole.getAdminRole().name();
        String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getPhoneNumber(), userType);
        String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getPhoneNumber());

        log.info("Admin login successful: {} - {}", user.getUserId(), adminRole.getAdminRole());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userType(userType)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .isVerified(true)
                .loginTime(LocalDateTime.now())
                .build();
    }

    public List<AdminRegistrationResponse> getAllAdmins() {
        log.info("Getting all admin users");

        return adminRoleRepository.findAll().stream()
                .map(adminRole -> {
                    User user = adminRole.getUser();
                    return AdminRegistrationResponse.builder()
                            .success(true)
                            .message("Admin user found")
                            .adminId(user.getUserId())
                            .email(user.getEmail())
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .adminRole(adminRole.getAdminRole())
                            .createdAt(user.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    public AuthResponse getAdminProfile(UUID adminId) {
        log.info("Getting admin profile: {}", adminId);

        AdminRole adminRole = adminRoleRepository.findByIdWithUser(adminId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Admin not found"));

        User user = adminRole.getUser();
        String userType = "ADMIN_" + adminRole.getAdminRole().name();

        return AuthResponse.builder()
                .userId(user.getUserId())
                .userType(userType)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .isVerified(true)
                .loginTime(adminRole.getLastLogin())
                .build();
    }

    @Transactional
    public AdminRegistrationResponse deactivateAdmin(UUID adminId) {
        log.info("Deactivating admin: {}", adminId);

        AdminRole adminRole = adminRoleRepository.findByIdWithUser(adminId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Admin not found"));

        User user = adminRole.getUser();
        user.setIsActive(false);
        userRepository.save(user);

        log.info("Admin deactivated: {}", adminId);

        return AdminRegistrationResponse.builder()
                .success(true)
                .message("Admin user deactivated successfully")
                .adminId(user.getUserId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .adminRole(adminRole.getAdminRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}