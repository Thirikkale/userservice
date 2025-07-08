package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Driver;
import com.thirikkale.userservice.service.FirebaseAuthService.FirebaseUserInfo;
import com.thirikkale.userservice.model.Rider;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.model.enums.Gender;
import com.thirikkale.userservice.repository.DriverRepository;
import com.thirikkale.userservice.repository.RiderRepository;
import com.thirikkale.userservice.repository.UserRepository;

import com.thirikkale.userservice.util.PhoneNumberValidator;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;


import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;


@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MultiRoleAuthService {

    private final UserRepository userRepository;
    private final RiderRepository riderRepository;
    private final DriverRepository driverRepository;
    private final FirebaseAuthService firebaseAuthService;
    private final JwtService jwtService;
    private final PhoneNumberValidator phoneNumberValidator;

    @PersistenceContext
    private EntityManager entityManager;

    public enum AppType {
        RIDER_APP,
        DRIVER_APP
    }

    public enum UserRole {
        RIDER_ONLY,
        DRIVER_ONLY,
        BOTH_ROLES,
        NEW_USER
    }

    /**
     * Universal registration method for both rider and driver apps
     */
    public AuthResponse registerUser(String firebaseIdToken, String firstName,
                                     String lastName, String whatsappNumber,
                                     AppType appType) {

        log.info("Processing registration for {} app", appType);

        // 1. Verify Firebase token
        FirebaseUserInfo firebaseUserInfo = firebaseAuthService.extractUserInfo(firebaseIdToken);
        if (!firebaseUserInfo.isPhoneVerified()) {
            throw new CustomExceptions.InvalidTokenException("Phone number not verified in Firebase");
        }

        String formattedPhone = phoneNumberValidator.formatToE164(firebaseUserInfo.getPhoneNumber());

        // 2. Check existing user and determine role status
        UserRoleStatus roleStatus = checkUserRoleStatus(formattedPhone);

        // 3. Handle different scenarios based on app type and existing roles
        return handleRegistrationScenario(roleStatus, firebaseUserInfo, firstName,
                lastName, whatsappNumber, appType, formattedPhone);
    }

    /**
     * Check what roles a user currently has
     */
    private UserRoleStatus checkUserRoleStatus(String phoneNumber) {
        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);

        if (existingUser.isEmpty()) {
            return new UserRoleStatus(null, UserRole.NEW_USER, false, false);
        }

        User user = existingUser.get();
        boolean hasRiderRole = userRepository.hasRiderRole(phoneNumber);
        boolean hasDriverRole = userRepository.hasDriverRole(phoneNumber);

        UserRole currentRole;
        if (hasRiderRole && hasDriverRole) {
            currentRole = UserRole.BOTH_ROLES;
        } else if (hasRiderRole) {
            currentRole = UserRole.RIDER_ONLY;
        } else if (hasDriverRole) {
            currentRole = UserRole.DRIVER_ONLY;
        } else {
            currentRole = UserRole.NEW_USER; // User exists but no roles (shouldn't happen)
        }

        return new UserRoleStatus(user, currentRole, hasRiderRole, hasDriverRole);
    }

    /**
     * Handle different registration scenarios
     */
    private AuthResponse handleRegistrationScenario(UserRoleStatus roleStatus,
                                                    FirebaseUserInfo firebaseUserInfo,
                                                    String firstName, String lastName,
                                                    String whatsappNumber, AppType appType,
                                                    String formattedPhone) {

        switch (appType) {
            case RIDER_APP:
                return handleRiderAppRegistration(roleStatus, firebaseUserInfo, firstName,
                        lastName, formattedPhone);

            case DRIVER_APP:
                return handleDriverAppRegistration(roleStatus, firebaseUserInfo, firstName,
                        lastName, whatsappNumber, formattedPhone);

            default:
                throw new IllegalArgumentException("Invalid app type");
        }
    }

    /**
     * Handle Rider App Registration Logic
     */
    private AuthResponse handleRiderAppRegistration(UserRoleStatus roleStatus,
                                                    FirebaseUserInfo firebaseUserInfo,
                                                    String firstName, String lastName,
                                                    String formattedPhone) {

        switch (roleStatus.getCurrentRole()) {
            case NEW_USER:
                // Create new user + rider profile
                log.info("Creating new user with rider role for phone: {}", formattedPhone);
                return createNewUserWithRiderRole(firebaseUserInfo, firstName, lastName, formattedPhone);

            case RIDER_ONLY:
                // Already a rider - block registration, redirect to login
                log.warn("User already has rider role, blocking registration: {}", formattedPhone);
                throw new CustomExceptions.UserAlreadyExistsException(
                        "You already have a Rider account. Please login instead.");

            case DRIVER_ONLY:
                // Driver wants to become rider too - upgrade existing user
                log.info("Upgrading driver to also be a rider: {}", formattedPhone);
                return upgradeDriverToRider(roleStatus.getUser(), formattedPhone);

            case BOTH_ROLES:
                // Already has both roles - block registration
                log.warn("User already has both roles, blocking registration: {}", formattedPhone);
                throw new CustomExceptions.UserAlreadyExistsException(
                        "You already have accounts for both Rider and Driver. Please login instead.");

            default:
                throw new IllegalStateException("Invalid user role state");
        }
    }

    /**
     * Handle Driver App Registration Logic
     */
    private AuthResponse handleDriverAppRegistration(UserRoleStatus roleStatus,
                                                     FirebaseUserInfo firebaseUserInfo,
                                                     String firstName, String lastName,
                                                     String whatsappNumber, String formattedPhone) {

        switch (roleStatus.getCurrentRole()) {
            case NEW_USER:
                // Create new user + driver profile
                log.info("Creating new user with driver role for phone: {}", formattedPhone);
                return createNewUserWithDriverRole(firebaseUserInfo, firstName, lastName,
                        whatsappNumber, formattedPhone);

            case DRIVER_ONLY:
                // Already a driver - block registration, redirect to login
                log.warn("User already has driver role, blocking registration: {}", formattedPhone);
                throw new CustomExceptions.UserAlreadyExistsException(
                        "You already have a Driver account. Please login instead.");

            case RIDER_ONLY:
                // Rider wants to become driver too - upgrade existing user
                log.info("Upgrading rider to also be a driver: {}", formattedPhone);
                return upgradeRiderToDriver(roleStatus.getUser(), whatsappNumber, formattedPhone);

            case BOTH_ROLES:
                // Already has both roles - block registration
                log.warn("User already has both roles, blocking registration: {}", formattedPhone);
                throw new CustomExceptions.UserAlreadyExistsException(
                        "You already have accounts for both Rider and Driver. Please login instead.");

            default:
                throw new IllegalStateException("Invalid user role state");
        }
    }

    /**
     * Create new user with rider role - FIXED TPT implementation
     */
    private AuthResponse createNewUserWithRiderRole(FirebaseUserInfo firebaseUserInfo,
                                                    String firstName, String lastName,
                                                    String formattedPhone) {

        log.info("Creating new user with rider role for phone: {}", formattedPhone);

        try {
            // 1. Create and persist user first to get the generated ID
            User user = User.builder()
                    .phoneNumber(formattedPhone)
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(firebaseUserInfo.getEmail())
                    .isActive(true)
                    .isPhoneVerified(true)
                    .isEmailVerified(firebaseUserInfo.isEmailVerified())
                    .lastLoginAt(LocalDateTime.now())
                    .profilePhotoUrl(firebaseUserInfo.getPicture())
                    .build();

            // Use EntityManager to persist and flush immediately
            entityManager.persist(user);
            entityManager.flush(); // Ensure user is saved and ID is generated

            // Ensure user has been persisted and has an ID
            if (user.getUserId() == null) {
                throw new RuntimeException("Failed to generate user ID during persistence");
            }

            log.debug("User created with ID: {}", user.getUserId());

            // 2. Create rider profile with the generated user ID (TPT relationship)
            Rider rider = Rider.builder()
                    .riderId(user.getUserId()) // TPT: PK = FK - now user has a valid ID
                    .user(user)
                    .gender(Gender.NOT_SPECIFIED)
                    .genderVerified(false)
                    .totalRides(0)
                    .rating(0.0)
                    .womenOnlyAccess(false)
                    .preferredPaymentMethod("CASH")
                    .build();

            // Use EntityManager to persist rider directly
            entityManager.persist(rider);
            entityManager.flush(); // Ensure rider is saved

            log.info("Successfully created user with rider role: {}", user.getUserId());
            return generateAuthResponse(user, "RIDER");

        } catch (Exception e) {
            log.error("Failed to create user with rider role for phone: {}", formattedPhone, e);
            throw new RuntimeException("User registration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create new user with driver role - FIXED TPT implementation
     */
    private AuthResponse createNewUserWithDriverRole(FirebaseUserInfo firebaseUserInfo,
                                                     String firstName, String lastName,
                                                     String whatsappNumber, String formattedPhone) {

        log.info("Creating new user with driver role for phone: {}", formattedPhone);

        try {
            // 1. Create and persist user first to get the generated ID
            User user = User.builder()
                    .phoneNumber(formattedPhone)
                    // Handle null names gracefully with default values
                    .firstName(firstName != null && !firstName.trim().isEmpty() ? firstName.trim() : "Driver")
                    .lastName(lastName != null && !lastName.trim().isEmpty() ? lastName.trim() : "User")
                    .email(firebaseUserInfo.getEmail())
                    .isActive(true)
                    .isPhoneVerified(true)
                    .isEmailVerified(firebaseUserInfo.isEmailVerified())
                    .lastLoginAt(LocalDateTime.now())
                    .profilePhotoUrl(firebaseUserInfo.getPicture())
                    .build();

            // Use EntityManager to persist and flush immediately
            entityManager.persist(user);
            entityManager.flush();

            // Ensure user has been persisted and has an ID
            if (user.getUserId() == null) {
                throw new RuntimeException("Failed to generate user ID during persistence");
            }

            log.debug("User created with ID: {}", user.getUserId());

            // 2. Create driver profile with the generated user ID (TPT relationship)
            Driver driver = Driver.builder()
                    .driverId(user.getUserId()) // TPT: PK = FK - now user has a valid ID
                    .user(user)
                    .whatsappNumber(whatsappNumber != null ? whatsappNumber : formattedPhone)
                    .isAvailable(false)
                    .isVerified(false)
                    .isDocumentsUploaded(false)
                    .faceVerificationStatus("PENDING")
                    .documentVerificationStatus("PENDING")
                    .profileExtractionStatus("PENDING")
                    .build();

            // Use EntityManager to persist driver directly
            entityManager.persist(driver);
            entityManager.flush(); // Ensure driver is saved

            log.info("Successfully created user with driver role: {}", user.getUserId());
            return generateAuthResponse(user, "DRIVER");

        } catch (Exception e) {
            log.error("Failed to create user with driver role for phone: {}", formattedPhone, e);
            throw new RuntimeException("Driver registration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Upgrade existing driver to also be a rider - preserves TPT integrity
     */
    private AuthResponse upgradeDriverToRider(User existingUser, String formattedPhone) {

        log.info("Upgrading driver to also be a rider for user: {}", existingUser.getUserId());

        try {
            // Validate that user ID exists
            if (existingUser.getUserId() == null) {
                throw new IllegalStateException("Cannot upgrade user with null ID");
            }

            // Check if rider already exists (safety check)
            if (riderRepository.existsByUser_PhoneNumber(formattedPhone)) {
                log.warn("Rider already exists for phone: {}", formattedPhone);
                throw new CustomExceptions.UserAlreadyExistsException("Rider profile already exists");
            }

            // Update user login time
            existingUser.setLastLoginAt(LocalDateTime.now());
            existingUser = userRepository.save(existingUser);

            // Create rider profile for existing user using same UUID
            Rider newRider = Rider.builder()
                    .riderId(existingUser.getUserId()) // TPT: same ID as user
                    .user(existingUser)
                    .gender(Gender.NOT_SPECIFIED)
                    .genderVerified(false)
                    .totalRides(0)
                    .rating(0.0)
                    .womenOnlyAccess(false)
                    .preferredPaymentMethod("CASH")
                    .build();

            // Use EntityManager for consistent persistence
            entityManager.persist(newRider);
            entityManager.flush();

            log.info("Successfully upgraded user {} to have both driver and rider roles",
                    existingUser.getUserId());

            return generateAuthResponse(existingUser, "RIDER"); // Login as rider since they used rider app

        } catch (Exception e) {
            log.error("Failed to upgrade driver to rider for user: {}", existingUser.getUserId(), e);
            throw new RuntimeException("Role upgrade failed: " + e.getMessage(), e);
        }
    }

    /**
     * Upgrade existing rider to also be a driver - preserves TPT integrity
     */
    private AuthResponse upgradeRiderToDriver(User existingUser, String whatsappNumber,
                                              String formattedPhone) {

        log.info("Upgrading rider to also be a driver for user: {}", existingUser.getUserId());

        try {
            // Validate that user ID exists
            if (existingUser.getUserId() == null) {
                throw new IllegalStateException("Cannot upgrade user with null ID");
            }

            // Check if driver already exists (safety check)
            if (driverRepository.existsByUser_PhoneNumber(formattedPhone)) {
                log.warn("Driver already exists for phone: {}", formattedPhone);
                throw new CustomExceptions.UserAlreadyExistsException("Driver profile already exists");
            }

            // Update user login time
            existingUser.setLastLoginAt(LocalDateTime.now());
            existingUser = userRepository.save(existingUser);

            // Create driver profile for existing user using same UUID
            Driver newDriver = Driver.builder()
                    .driverId(existingUser.getUserId()) // TPT: same ID as user
                    .user(existingUser)
                    .whatsappNumber(whatsappNumber != null ? whatsappNumber : formattedPhone)
                    .isAvailable(false)
                    .isVerified(false)
                    .isDocumentsUploaded(false)
                    .faceVerificationStatus("PENDING")
                    .documentVerificationStatus("PENDING")
                    .profileExtractionStatus("PENDING")
                    .build();

            // Use EntityManager for consistent persistence
            entityManager.persist(newDriver);
            entityManager.flush();

            log.info("Successfully upgraded user {} to have both rider and driver roles",
                    existingUser.getUserId());

            return generateAuthResponse(existingUser, "DRIVER"); // Login as driver since they used driver app

        } catch (Exception e) {
            log.error("Failed to upgrade rider to driver for user: {}", existingUser.getUserId(), e);
            throw new RuntimeException("Role upgrade failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate appropriate auth response based on role
     */
    private AuthResponse generateAuthResponse(User user, String loginRole) {
        String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getPhoneNumber(), loginRole);
        String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getPhoneNumber());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userType(loginRole)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .isVerified(user.getIsPhoneVerified())
                .loginTime(LocalDateTime.now())
                .build();
    }

    // Helper class for role status
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class UserRoleStatus {
        private User user;
        private UserRole currentRole;
        private boolean hasRiderRole;
        private boolean hasDriverRole;
    }
}