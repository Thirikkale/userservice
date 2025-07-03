package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.request.RiderRegistrationRequest;
import com.thirikkale.userservice.dto.request.RiderProfileUpdateRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.dto.response.RiderResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Rider;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.model.enums.Gender;
import com.thirikkale.userservice.repository.RiderRepository;
import com.thirikkale.userservice.repository.UserRepository;
import com.thirikkale.userservice.service.FirebaseAuthService.FirebaseUserInfo;
import com.thirikkale.userservice.util.PhoneNumberValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiderService {

    private final UserRepository userRepository;
    private final RiderRepository riderRepository;
    private final FirebaseAuthService firebaseAuthService;
    private final JwtService jwtService;
    private final PhoneNumberValidator phoneNumberValidator;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public AuthResponse registerRider(RiderRegistrationRequest request) {
        log.info("Starting rider registration process");

        try {
            // 1. Verify Firebase token
            FirebaseUserInfo firebaseUserInfo = firebaseAuthService.extractUserInfo(request.getFirebaseIdToken());

            if (!firebaseUserInfo.isPhoneVerified()) {
                throw new CustomExceptions.InvalidTokenException("Phone number not verified in Firebase");
            }

            // 2. Format phone number
            String formattedPhone = phoneNumberValidator.formatToE164(firebaseUserInfo.getPhoneNumber());
            log.info("Firebase token verified for phone: {}", formattedPhone);

            // 3. Check if rider already exists first
            if (riderRepository.existsByUser_PhoneNumber(formattedPhone)) {
                log.warn("Rider already exists for phone: {}", formattedPhone);
                throw new CustomExceptions.UserAlreadyExistsException("Rider already registered with this phone number");
            }

            // 4. Create or get user
            User user = getOrCreateUser(formattedPhone, firebaseUserInfo, request);

            // 5. Final check before creating rider
            if (riderRepository.existsByUser_PhoneNumber(formattedPhone)) {
                log.warn("Rider was created by another thread for phone: {}", formattedPhone);
                throw new CustomExceptions.UserAlreadyExistsException("Rider already registered with this phone number");
            }

            // 6. Create new rider using EntityManager for direct persistence
            Rider rider = createNewRiderDirectly(user);

            log.info("Successfully registered rider: {}", rider.getRiderId());
            return createAuthResponse(rider, formattedPhone);

        } catch (CustomExceptions.UserAlreadyExistsException e) {
            log.error("User already exists: {}", e.getMessage());
            throw e;
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation during rider registration: {}", e.getMessage());
            throw new CustomExceptions.UserAlreadyExistsException("Rider already registered with this phone number");
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure during rider registration: {}", e.getMessage());
            throw new CustomExceptions.UserAlreadyExistsException("Registration failed due to concurrent access. Please try again.");
        } catch (Exception e) {
            log.error("Unexpected error during rider registration", e);
            throw new RuntimeException("Registration failed. Please try again.");
        }
    }

    private User getOrCreateUser(String formattedPhone, FirebaseUserInfo firebaseUserInfo, RiderRegistrationRequest request) {
        Optional<User> existingUser = userRepository.findByPhoneNumber(formattedPhone);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.info("Found existing user for phone: {}", formattedPhone);

            // Only update login time to avoid conflicts
            user.setLastLoginAt(LocalDateTime.now());

            try {
                return userRepository.save(user);
            } catch (Exception e) {
                log.warn("Failed to update user login time, returning existing user: {}", e.getMessage());
                return user;
            }
        } else {
            // Create new user
            User newUser = User.builder()
                    .phoneNumber(formattedPhone)
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(firebaseUserInfo.getEmail())
                    .isActive(true)
                    .isPhoneVerified(true)
                    .isEmailVerified(firebaseUserInfo.isEmailVerified())
                    .lastLoginAt(LocalDateTime.now())
                    .profilePhotoUrl(firebaseUserInfo.getPicture())
                    .build();

            return userRepository.save(newUser);
        }
    }

    private Rider createNewRiderDirectly(User user) {
        try {
            // Clear the persistence context to avoid conflicts
            entityManager.clear();

            // Create a fresh rider entity
            Rider rider = new Rider();
            rider.setRiderId(user.getUserId());

            // Re-attach the user to the current persistence context
            User managedUser = entityManager.merge(user);
            rider.setUser(managedUser);

            // Set default values manually
            rider.setGender(Gender.NOT_SPECIFIED);
            rider.setGenderVerified(false);
            rider.setTotalRides(0);
            rider.setRating(0.0);
            rider.setWomenOnlyAccess(false);
            rider.setPreferredPaymentMethod("CASH");

            // Set timestamps manually
            LocalDateTime now = LocalDateTime.now();
            rider.setCreatedAt(now);
            rider.setUpdatedAt(now);

            // Use persist instead of save to ensure it's treated as a new entity
            entityManager.persist(rider);
            entityManager.flush(); // Force the persistence

            log.info("Successfully created new rider with ID: {}", rider.getRiderId());
            return rider;

        } catch (Exception e) {
            log.error("Error creating new rider for user {}: {}", user.getUserId(), e.getMessage());
            throw new RuntimeException("Failed to create rider profile", e);
        }
    }

    private AuthResponse createAuthResponse(Rider rider, String phoneNumber) {
        String accessToken = jwtService.generateAccessToken(
                rider.getRiderId(),
                phoneNumber,
                "RIDER"
        );

        String refreshToken = jwtService.generateRefreshToken(
                rider.getRiderId(),
                phoneNumber
        );

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userId(rider.getRiderId())
                .userType("RIDER")
                .phoneNumber(phoneNumber)
                .firstName(rider.getUser().getFirstName())
                .lastName(rider.getUser().getLastName())
                .email(rider.getUser().getEmail())
                .isActive(rider.getUser().getIsActive())
                .isVerified(rider.getUser().getIsPhoneVerified())
                .loginTime(LocalDateTime.now())
                .build();
    }

    @Transactional
    public RiderResponse updateRiderProfile(UUID riderId, RiderProfileUpdateRequest request) {
        log.info("Updating rider profile: {}", riderId);

        Rider rider = riderRepository.findByIdWithUser(riderId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Rider not found"));

        User user = rider.getUser();

        // Update user information
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getEmergencyContactName() != null) {
            user.setEmergencyContactName(request.getEmergencyContactName());
        }
        if (request.getEmergencyContactPhone() != null) {
            user.setEmergencyContactPhone(request.getEmergencyContactPhone());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }

        // Update rider-specific information
        if (request.getPreferredPaymentMethod() != null) {
            rider.setPreferredPaymentMethod(request.getPreferredPaymentMethod());
        }

        // Save changes
        userRepository.save(user);
        rider = riderRepository.save(rider);

        log.info("Rider profile updated successfully: {}", riderId);
        return mapToRiderResponse(rider);
    }

    public RiderResponse getRiderById(UUID riderId) {
        log.info("Getting rider by ID: {}", riderId);
        Rider rider = riderRepository.findByIdWithUser(riderId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Rider not found"));
        return mapToRiderResponse(rider);
    }

    public RiderResponse getRiderByPhoneNumber(String phoneNumber) {
        log.info("Getting rider by phone: {}", phoneNumber);
        Rider rider = riderRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Rider not found"));
        return mapToRiderResponse(rider);
    }

    public List<RiderResponse> getAllRiders() {
        log.info("Getting all riders");
        return riderRepository.findAll().stream()
                .map(this::mapToRiderResponse)
                .collect(Collectors.toList());
    }

    private RiderResponse mapToRiderResponse(Rider rider) {
        User user = rider.getUser();
        return RiderResponse.builder()
                .riderId(rider.getRiderId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .dateOfBirth(user.getDateOfBirth())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .emergencyContactName(user.getEmergencyContactName())
                .emergencyContactPhone(user.getEmergencyContactPhone())
                .gender(rider.getGender())
                .womenOnlyAccess(rider.getWomenOnlyAccess())
                .genderVerified(rider.getGenderVerified())
                .selfieUrl(rider.getSelfieUrl())
                .rating(rider.getRating())
                .totalRides(rider.getTotalRides())
                .lastRideDate(rider.getLastRideDate())
                .preferredPaymentMethod(rider.getPreferredPaymentMethod())
                .isActive(user.getIsActive())
                .isPhoneVerified(user.getIsPhoneVerified())
                .createdAt(rider.getCreatedAt())
                .build();
    }
}