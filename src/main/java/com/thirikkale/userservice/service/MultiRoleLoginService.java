package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.repository.DriverRepository;
import com.thirikkale.userservice.repository.RiderRepository;
import com.thirikkale.userservice.repository.UserRepository;
import com.thirikkale.userservice.service.FirebaseAuthService.FirebaseUserInfo;
import com.thirikkale.userservice.util.PhoneNumberValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MultiRoleLoginService {

    private final UserRepository userRepository;
    private final RiderRepository riderRepository;
    private final DriverRepository driverRepository;
    private final FirebaseAuthService firebaseAuthService;
    private final JwtService jwtService;
    private final PhoneNumberValidator phoneNumberValidator;

    /**
     * App-specific login that validates user has the required role
     */
    public AuthResponse loginForApp(String firebaseIdToken, MultiRoleAuthService.AppType appType) {
        log.info("Processing login for {} app", appType);

        // 1. Verify Firebase token
        FirebaseUserInfo firebaseUserInfo = firebaseAuthService.extractUserInfo(firebaseIdToken);
        if (!firebaseUserInfo.isPhoneVerified()) {
            throw new CustomExceptions.PhoneNotVerifiedException("Phone number not verified in Firebase");
        }

        String formattedPhone = phoneNumberValidator.formatToE164(firebaseUserInfo.getPhoneNumber());

        // 2. Find user
        User user = userRepository.findByPhoneNumber(formattedPhone)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException(
                        "No account found. Please sign up first."));

        if (!user.getIsActive()) {
            throw new CustomExceptions.UserNotActiveException("Account is deactivated");
        }

        // 3. Validate user has required role for this app
        validateUserRoleForApp(user, appType, formattedPhone);

        // 4. Update login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 5. Generate appropriate token
        String userType = determineLoginUserType(appType);
        String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getPhoneNumber(), userType);
        String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getPhoneNumber());

        log.info("Login successful for {} app - user: {}", appType, user.getUserId());

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
                .isVerified(user.getIsPhoneVerified())
                .loginTime(LocalDateTime.now())
                .build();
    }

    /**
     * Validate that user has the required role for the requesting app
     */
    private void validateUserRoleForApp(User user, MultiRoleAuthService.AppType appType, String phoneNumber) {
        switch (appType) {
            case RIDER_APP:
                if (!userRepository.hasRiderRole(phoneNumber)) {
                    throw new CustomExceptions.UnauthorizedAppAccessException(
                            "You don't have a Rider account. Please sign up for the Rider app first.");
                }
                break;

            case DRIVER_APP:
                if (!userRepository.hasDriverRole(phoneNumber)) {
                    throw new CustomExceptions.UnauthorizedAppAccessException(
                            "You don't have a Driver account. Please sign up for the Driver app first.");
                }
                break;

            default:
                throw new IllegalArgumentException("Invalid app type");
        }
    }

    /**
     * Determine the user type for JWT token based on app
     */
    private String determineLoginUserType(MultiRoleAuthService.AppType appType) {
        switch (appType) {
            case RIDER_APP:
                return "RIDER";
            case DRIVER_APP:
                return "DRIVER";
            default:
                throw new IllegalArgumentException("Invalid app type");
        }
    }
}