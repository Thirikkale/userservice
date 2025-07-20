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
import java.util.UUID;

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
     * Step 1: Firebase token-only registration - Authentication and role assignment
     */
    public AuthResponse registerUserWithFirebaseOnly(String firebaseIdToken, AppType appType) {
        log.info("Processing token-only registration for {} app", appType);

        // 1. Verify Firebase token
        FirebaseUserInfo firebaseUserInfo = firebaseAuthService.extractUserInfo(firebaseIdToken);
        if (!firebaseUserInfo.isPhoneVerified()) {
            throw new CustomExceptions.InvalidTokenException("Phone number not verified in Firebase");
        }

        String formattedPhone = phoneNumberValidator.formatToE164(firebaseUserInfo.getPhoneNumber());

        // 2. Check existing user and determine role status
        UserRoleStatus roleStatus = checkUserRoleStatus(formattedPhone);

        // 3. Handle different scenarios based on app type and existing roles
        return handleRegistrationScenarioTokenOnly(roleStatus, firebaseUserInfo, appType, formattedPhone);
    }

    /**
     * Step 2: Complete profile setup with names
     */
    @Transactional
    public AuthResponse completeProfileSetup(UUID userId, String firstName, String lastName,
                                             String whatsappNumber, AppType appType) {
        log.info("Completing profile setup for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found"));

        // Update user profile
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user = userRepository.save(user);

        // Update driver-specific fields if applicable
        if (appType == AppType.DRIVER_APP && whatsappNumber != null) {
            Driver driver = driverRepository.findByIdWithUser(userId)
                    .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));
            driver.setWhatsappNumber(whatsappNumber);
            driverRepository.save(driver);
        }

        // Generate final auth response
        String userType = determineLoginUserType(appType);
        String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getPhoneNumber(), userType);
        String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getPhoneNumber());

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
                .isActive(user.getIsActive())
                .isVerified(user.getIsPhoneVerified())
                .loginTime(LocalDateTime.now())
                .isNewRegistration(false)
                .registrationMessage("Profile setup completed successfully!")
                .nextStep(appType == AppType.DRIVER_APP ?
                        "Complete your profile and upload required documents to start driving." :
                        "You're all set! Start using the app.")
                .build();
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
            currentRole = UserRole.NEW_USER;
        }

        return new UserRoleStatus(user, currentRole, hasRiderRole, hasDriverRole);
    }

    /**
     * Handle token-only registration scenarios
     */
    private AuthResponse handleRegistrationScenarioTokenOnly(UserRoleStatus roleStatus,
                                                             FirebaseUserInfo firebaseUserInfo,
                                                             AppType appType, String formattedPhone) {
        switch (appType) {
            case RIDER_APP:
                return handleRiderAppRegistrationTokenOnly(roleStatus, firebaseUserInfo, formattedPhone);
            case DRIVER_APP:
                return handleDriverAppRegistrationTokenOnly(roleStatus, firebaseUserInfo, formattedPhone);
            default:
                throw new IllegalArgumentException("Invalid app type");
        }
    }

    /**
     * Handle Rider App token-only registration
     */
    private AuthResponse handleRiderAppRegistrationTokenOnly(UserRoleStatus roleStatus,
                                                             FirebaseUserInfo firebaseUserInfo,
                                                             String formattedPhone) {
        switch (roleStatus.getCurrentRole()) {
            case NEW_USER:
                return createNewUserWithRiderRoleTokenOnly(firebaseUserInfo, formattedPhone);
            case RIDER_ONLY:
                return performAutoLogin(roleStatus.getUser(), "RIDER",
                        "Welcome back! You're already registered as a rider.");
            case DRIVER_ONLY:
                return upgradeDriverToRider(roleStatus.getUser(), formattedPhone);
            case BOTH_ROLES:
                return performAutoLogin(roleStatus.getUser(), "RIDER",
                        "Welcome back! Logging you into the Rider app.");
            default:
                throw new IllegalStateException("Invalid user role state");
        }
    }

    /**
     * Handle Driver App token-only registration
     */
    private AuthResponse handleDriverAppRegistrationTokenOnly(UserRoleStatus roleStatus,
                                                              FirebaseUserInfo firebaseUserInfo,
                                                              String formattedPhone) {
        switch (roleStatus.getCurrentRole()) {
            case NEW_USER:
                return createNewUserWithDriverRoleTokenOnly(firebaseUserInfo, formattedPhone);
            case DRIVER_ONLY:
                return performAutoLogin(roleStatus.getUser(), "DRIVER",
                        "Welcome back! You're already registered as a driver.");
            case RIDER_ONLY:
                return upgradeRiderToDriverTokenOnly(roleStatus.getUser(), formattedPhone);
            case BOTH_ROLES:
                return performAutoLogin(roleStatus.getUser(), "DRIVER",
                        "Welcome back! Logging you into the Driver app.");
            default:
                throw new IllegalStateException("Invalid user role state");
        }
    }

    /**
     * Create new user with rider role - token only (no names yet)
     */
    private AuthResponse createNewUserWithRiderRoleTokenOnly(FirebaseUserInfo firebaseUserInfo, String formattedPhone) {
        log.info("Creating new user with rider role (token-only) for phone: {}", formattedPhone);

        try {
            // Create user with minimal info
            User user = User.builder()
                    .phoneNumber(formattedPhone)
                    .firstName("Rider") // Temporary placeholder
                    .lastName("User") // Temporary placeholder
                    .email(firebaseUserInfo.getEmail())
                    .isActive(true)
                    .isPhoneVerified(true)
                    .isEmailVerified(firebaseUserInfo.isEmailVerified())
                    .lastLoginAt(LocalDateTime.now())
                    .profilePhotoUrl(firebaseUserInfo.getPicture())
                    .build();

            entityManager.persist(user);
            entityManager.flush();

            // Create rider profile
            Rider rider = Rider.builder()
                    .riderId(user.getUserId())
                    .user(user)
                    .gender(Gender.NOT_SPECIFIED)
                    .genderVerified(false)
                    .totalRides(0)
                    .rating(0.0)
                    .womenOnlyAccess(false)
                    .preferredPaymentMethod("CASH")
                    .build();

            entityManager.persist(rider);
            entityManager.flush();

            return generateTokenOnlyRegistrationResponse(user, "RIDER");

        } catch (Exception e) {
            log.error("Failed to create user with rider role (token-only) for phone: {}", formattedPhone, e);
            throw new RuntimeException("User registration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create new user with driver role - token only (no names yet)
     */
    private AuthResponse createNewUserWithDriverRoleTokenOnly(FirebaseUserInfo firebaseUserInfo, String formattedPhone) {
        log.info("Creating new user with driver role (token-only) for phone: {}", formattedPhone);

        try {
            // Create user with minimal info
            User user = User.builder()
                    .phoneNumber(formattedPhone)
                    .firstName("Driver") // Temporary placeholder
                    .lastName("User") // Temporary placeholder
                    .email(firebaseUserInfo.getEmail())
                    .isActive(true)
                    .isPhoneVerified(true)
                    .isEmailVerified(firebaseUserInfo.isEmailVerified())
                    .lastLoginAt(LocalDateTime.now())
                    .profilePhotoUrl(firebaseUserInfo.getPicture())
                    .build();

            entityManager.persist(user);
            entityManager.flush();

            // Create driver profile
            Driver driver = Driver.builder()
                    .driverId(user.getUserId())
                    .user(user)
                    .isAvailable(false)
                    .isVerified(false)
                    .isDocumentsUploaded(false)
                    .faceVerificationStatus("PENDING")
                    .documentVerificationStatus("PENDING")
                    .profileExtractionStatus("PENDING")
                    .build();

            entityManager.persist(driver);
            entityManager.flush();

            return generateTokenOnlyRegistrationResponse(user, "DRIVER");

        } catch (Exception e) {
            log.error("Failed to create user with driver role (token-only) for phone: {}", formattedPhone, e);
            throw new RuntimeException("Driver registration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Perform auto-login for existing users
     */
    private AuthResponse performAutoLogin(User existingUser, String loginRole, String welcomeMessage) {
        log.info("Performing auto-login for existing user: {} as {}", existingUser.getUserId(), loginRole);

        // Update login time
        existingUser.setLastLoginAt(LocalDateTime.now());
        existingUser = userRepository.save(existingUser);

        String accessToken = jwtService.generateAccessToken(existingUser.getUserId(), existingUser.getPhoneNumber(), loginRole);
        String refreshToken = jwtService.generateRefreshToken(existingUser.getUserId(), existingUser.getPhoneNumber());

        return AuthResponse.autoLogin(
                existingUser.getUserId(),
                accessToken,
                refreshToken,
                loginRole,
                existingUser.getFirstName(),
                existingUser.getLastName(),
                existingUser.getPhoneNumber(),
                existingUser.getEmail(),
                welcomeMessage
        );
    }

    /**
     * Upgrade existing driver to also be a rider
     */
    private AuthResponse upgradeDriverToRider(User existingUser, String formattedPhone) {
        log.info("Upgrading driver to also be a rider for user: {}", existingUser.getUserId());

        try {
            if (existingUser.getUserId() == null) {
                throw new IllegalStateException("Cannot upgrade user with null ID");
            }

            if (riderRepository.existsByUser_PhoneNumber(formattedPhone)) {
                log.warn("Rider already exists for phone: {}", formattedPhone);
                throw new CustomExceptions.UserAlreadyExistsException("Rider profile already exists");
            }

            existingUser.setLastLoginAt(LocalDateTime.now());
            existingUser = userRepository.save(existingUser);

            Rider newRider = Rider.builder()
                    .riderId(existingUser.getUserId())
                    .user(existingUser)
                    .gender(Gender.NOT_SPECIFIED)
                    .genderVerified(false)
                    .totalRides(0)
                    .rating(0.0)
                    .womenOnlyAccess(false)
                    .preferredPaymentMethod("CASH")
                    .build();

            entityManager.persist(newRider);
            entityManager.flush();

            log.info("Successfully upgraded user {} to have both driver and rider roles", existingUser.getUserId());

            return performAutoLogin(existingUser, "RIDER", "Welcome! You now have access to both rider and driver features.");

        } catch (Exception e) {
            log.error("Failed to upgrade driver to rider for user: {}", existingUser.getUserId(), e);
            throw new RuntimeException("Role upgrade failed: " + e.getMessage(), e);
        }
    }

    /**
     * Upgrade rider to driver - token only
     */
    private AuthResponse upgradeRiderToDriverTokenOnly(User existingUser, String formattedPhone) {
        log.info("Upgrading rider to driver (token-only) for user: {}", existingUser.getUserId());

        try {
            Driver newDriver = Driver.builder()
                    .driverId(existingUser.getUserId())
                    .user(existingUser)
                    .isAvailable(false)
                    .isVerified(false)
                    .isDocumentsUploaded(false)
                    .faceVerificationStatus("PENDING")
                    .documentVerificationStatus("PENDING")
                    .profileExtractionStatus("PENDING")
                    .build();

            entityManager.persist(newDriver);
            entityManager.flush();

            return generateTokenOnlyRegistrationResponse(existingUser, "DRIVER");

        } catch (Exception e) {
            log.error("Failed to upgrade rider to driver (token-only) for user: {}", existingUser.getUserId(), e);
            throw new RuntimeException("Driver role upgrade failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate auth response for token-only registration (profile incomplete)
     */
    private AuthResponse generateTokenOnlyRegistrationResponse(User user, String userType) {
        String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getPhoneNumber(), userType);
        String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getPhoneNumber());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userType(userType)
                .firstName(user.getFirstName()) // Will be placeholder values
                .lastName(user.getLastName()) // Will be placeholder values
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .isActive(user.getIsActive())
                .isVerified(user.getIsPhoneVerified())
                .loginTime(LocalDateTime.now())
                .isNewRegistration(true)
                .registrationMessage("Registration successful! Please complete your profile.")
                .nextStep("COMPLETE_PROFILE") // Special flag for frontend
                .build();
    }

    private String determineLoginUserType(AppType appType) {
        switch (appType) {
            case RIDER_APP:
                return "RIDER";
            case DRIVER_APP:
                return "DRIVER";
            default:
                throw new IllegalArgumentException("Invalid app type");
        }
    }

    // Helper class for role status
    @Data
    @AllArgsConstructor
    private static class UserRoleStatus {
        private User user;
        private UserRole currentRole;
        private boolean hasRiderRole;
        private boolean hasDriverRole;
    }
}