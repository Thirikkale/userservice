package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.request.*;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.repository.UserRepository;
import com.thirikkale.userservice.service.FirebaseAuthService.FirebaseUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final FirebaseAuthService firebaseAuthService;

    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public AuthResponse register(RegisterRequest request) {
        log.info("Attempting to register user with email: {}", request.getEmail());

        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomExceptions.UserAlreadyExistsException("User with email already exists");
        }

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new CustomExceptions.UserAlreadyExistsException("User with phone number already exists");
        }

        // Create new user
        User user = User.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .isActive(true)
                .isEmailVerified(false) // Email verification can be added later
                .isPhoneVerified(false) // Phone verification via Firebase
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully: {}", user.getUserId());

        return generateAuthResponse(user);
    }

    public AuthResponse loginWithPassword(LoginRequest request) {
        log.info("Attempting password login for: {}", request.getEmailOrPhone());

        User user = findUserByEmailOrPhone(request.getEmailOrPhone());

        if (!user.getIsActive()) {
            throw new CustomExceptions.UserNotActiveException("User account is deactivated");
        }

        if (user.getPassword() == null) {
            throw new CustomExceptions.InvalidCredentialsException("Password not set for this account. Please use OTP login.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmailOrPhone(), request.getPassword())
            );
        } catch (Exception e) {
            throw new CustomExceptions.InvalidCredentialsException("Invalid credentials");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged in successfully with password: {}", user.getEmail());
        return generateAuthResponse(user);
    }

    // Firebase-based OTP login for existing users
    @Transactional
    public AuthResponse loginWithFirebase(String firebaseIdToken) {
        log.info("Attempting Firebase login");

        // Verify Firebase token and extract user info
        FirebaseUserInfo firebaseUserInfo = firebaseAuthService.extractUserInfo(firebaseIdToken);

        if (!firebaseUserInfo.isPhoneVerified()) {
            throw new CustomExceptions.PhoneNotVerifiedException("Phone number not verified in Firebase");
        }

        // Find existing user by phone number
        User user = userRepository.findByPhoneNumber(firebaseUserInfo.getPhoneNumber())
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found with phone number"));

        if (!user.getIsActive()) {
            throw new CustomExceptions.UserNotActiveException("User account is not active");
        }

        // Update phone verification status and last login
        user.setIsPhoneVerified(true);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged in successfully with Firebase: {}", user.getPhoneNumber());
        return generateAuthResponse(user);
    }

    private User findUserByEmailOrPhone(String emailOrPhone) {
        return userRepository.findByEmailOrPhoneNumber(emailOrPhone)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found"));
    }

    private AuthResponse generateAuthResponse(User user) {
        String userType = determineUserType(user);
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
                .isVerified(user.getIsPhoneVerified())
                .loginTime(LocalDateTime.now())
                .build();
    }

    // Update the determineUserType method:

    private String determineUserType(User user) {
        // Check if user is a rider
        if (userRepository.existsRiderByUserId(user.getUserId())) {
            return "RIDER";
        }

        // Check if user is a driver
        if (userRepository.existsDriverByUserId(user.getUserId())) {
            return "DRIVER";
        }

        // Check if user is admin (you can add admin role table check here)
        // For now, assume generic user
        return "USER";
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Refreshing access token");

        try {
            // Validate refresh token - use the single parameter method
            if (!jwtService.validateToken(refreshToken)) {
                throw new CustomExceptions.InvalidTokenException("Invalid refresh token");
            }

            // Extract user info from refresh token
            String phoneNumber = jwtService.extractPhoneNumber(refreshToken);

            // Find user
            User user = findUserByEmailOrPhone(phoneNumber);

            if (!user.getIsActive()) {
                throw new CustomExceptions.UserNotActiveException("User account is not active");
            }

            // Generate new tokens
            String newAccessToken = jwtService.generateAccessToken(
                    user.getUserId(),
                    user.getPhoneNumber(),
                    determineUserType(user)
            );
            String newRefreshToken = jwtService.generateRefreshToken(
                    user.getUserId(),
                    user.getPhoneNumber()
            );

            return AuthResponse.builder()
                    .userId(user.getUserId())
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .userType(determineUserType(user))
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .phoneNumber(user.getPhoneNumber())
                    .email(user.getEmail())
                    .isVerified(user.getIsPhoneVerified())
                    .loginTime(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw new CustomExceptions.InvalidTokenException("Token refresh failed");
        }
    }

    @Transactional
    public void logout(String token) {
        try {
            // Add token to blacklist (you can implement Redis-based blacklist)
            // For now, just log the logout
            String phoneNumber = jwtService.extractPhoneNumber(token);
            log.info("User logged out: {}", phoneNumber);

            // Optional: Update last logout time in database
            User user = findUserByEmailOrPhone(phoneNumber);
            // user.setLastLogoutAt(LocalDateTime.now());
            // userRepository.save(user);

        } catch (Exception e) {
            log.warn("Logout processing failed: {}", e.getMessage());
            // Don't throw exception for logout failures
        }
    }
}