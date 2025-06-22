package com.thirikkale.userservice.service;

import com.thirikkale.userservice.config.JwtConfig;
import com.thirikkale.userservice.dto.request.LoginRequest;
import com.thirikkale.userservice.dto.request.RegisterRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtConfig jwtConfig;

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
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getUserId());

        // Generate JWT token
        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.of(
                token,
                jwtConfig.getExpiration(),
                savedUser.getUserId(),
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getLastName()
        );
    }

    @Cacheable(value = "auth", key = "#request.emailOrPhone")
    public AuthResponse login(LoginRequest request) {
        log.info("Attempting to login user: {}", request.getEmailOrPhone());

        // Find user by email or phone
        User user = userRepository.findByEmail(request.getEmailOrPhone())
                .or(() -> userRepository.findByPhoneNumber(request.getEmailOrPhone()))
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found"));

        if (!user.getIsActive()) {
            throw new CustomExceptions.UserNotActiveException("User account is not active");
        }

        // Authenticate
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        request.getPassword()
                )
        );

        // Generate JWT token
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);

        log.info("User logged in successfully: {}", user.getEmail());

        return AuthResponse.of(
                token,
                jwtConfig.getExpiration(),
                user.getUserId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }
}