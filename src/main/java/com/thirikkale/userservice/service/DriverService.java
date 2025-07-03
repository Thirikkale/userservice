package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.request.DriverRegistrationRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.dto.response.DriverResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Driver;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.repository.DriverRepository;
import com.thirikkale.userservice.repository.UserRepository;
import com.thirikkale.userservice.service.FirebaseAuthService.FirebaseUserInfo;
import com.thirikkale.userservice.util.PhoneNumberValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final FirebaseAuthService firebaseAuthService;
    private final JwtService jwtService;
    private final PhoneNumberValidator phoneNumberValidator;
    private final OtpService otpService;

    @Transactional
    public AuthResponse registerDriver(DriverRegistrationRequest request) {
        log.info("Registering driver with Firebase token - simplified flow");

        try {
            // Verify Firebase token and extract user info
            FirebaseUserInfo firebaseUserInfo = firebaseAuthService.extractUserInfo(request.getFirebaseIdToken());

            if (!firebaseUserInfo.isPhoneVerified()) {
                throw new CustomExceptions.InvalidTokenException("Phone number not verified in Firebase");
            }

            // Format phone number
            String formattedPhone = phoneNumberValidator.formatToE164(firebaseUserInfo.getPhoneNumber());

            // Check Firebase rate limiting
            if (otpService.isFirebaseRateLimited(formattedPhone)) {
                long remainingMinutes = otpService.getFirebaseRateLimitExpiryMinutes(formattedPhone);
                throw new CustomExceptions.InvalidTokenException(
                        "Too many registration attempts. Please try again in " + remainingMinutes + " minutes.");
            }

            // Check if user already exists
            if (userRepository.existsByPhoneNumber(formattedPhone)) {
                throw new CustomExceptions.UserAlreadyExistsException("User with this phone number already exists");
            }

            // Create User with minimal info
            User user = User.builder()
                    .phoneNumber(formattedPhone)
                    .firstName(request.getFirstName() != null ? request.getFirstName() : "Driver")
                    .lastName(request.getLastName() != null ? request.getLastName() : "User")
                    .email(firebaseUserInfo.getEmail())
                    .profilePhotoUrl(firebaseUserInfo.getPicture())
                    .isActive(true)
                    .isPhoneVerified(true)
                    .isEmailVerified(firebaseUserInfo.isEmailVerified())
                    .lastLoginAt(LocalDateTime.now())
                    .build();

            // Create Driver with all required fields
            Driver driver = Driver.builder()
                    .driverId(user.getUserId()) // Set the ID before saving
                    .user(user)
                    .whatsappNumber(request.getWhatsappNumber() != null ? request.getWhatsappNumber() : formattedPhone)
                    .isAvailable(false)
                    .isVerified(false)
                    .isDocumentsUploaded(false)
                    .faceVerificationStatus("PENDING")
                    .documentVerificationStatus("PENDING")
                    .profileExtractionStatus("PENDING")
                    .build();

            // Save only the driver (cascade will save user)
            driver = driverRepository.save(driver);

            // Cache Firebase verification and reset attempts
            otpService.cacheFirebaseVerification(formattedPhone, firebaseUserInfo.getUid());
            otpService.resetFirebaseAttempts(formattedPhone);

            // Generate tokens
            String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getPhoneNumber(), "DRIVER");
            String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getPhoneNumber());

            log.info("Driver registered successfully: {}", user.getUserId());

            return AuthResponse.builder()
                    .userId(user.getUserId())
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .userType("DRIVER")
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .phoneNumber(user.getPhoneNumber())
                    .email(user.getEmail())
                    .isVerified(false)
                    .loginTime(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error during driver registration: {}", e.getMessage());
            if (e instanceof CustomExceptions.UserAlreadyExistsException ||
                    e instanceof CustomExceptions.InvalidTokenException) {
                throw e;
            }
            throw new RuntimeException("Driver registration failed. Please try again.");
        }
    }

    public DriverResponse getDriverById(UUID driverId) {
        log.info("Getting driver by ID: {}", driverId);

        Driver driver = driverRepository.findByIdWithUser(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        return mapToDriverResponse(driver);
    }

    public DriverResponse getDriverByPhoneNumber(String phoneNumber) {
        log.info("Getting driver by phone: {}", phoneNumber);

        Driver driver = driverRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        return mapToDriverResponse(driver);
    }

    public List<DriverResponse> getAllDrivers() {
        log.info("Getting all drivers");

        return driverRepository.findAll().stream()
                .map(this::mapToDriverResponse)
                .collect(Collectors.toList());
    }

    public List<DriverResponse> getAvailableDrivers() {
        log.info("Getting available drivers");

        return driverRepository.findAllAvailableAndVerified().stream()
                .map(this::mapToDriverResponse)
                .collect(Collectors.toList());
    }

    public List<DriverResponse> getPendingVerificationDrivers() {
        log.info("Getting drivers pending verification");

        return driverRepository.findAllPendingVerification().stream()
                .map(this::mapToDriverResponse)
                .collect(Collectors.toList());
    }

    public List<DriverResponse> getDriversPendingDocuments() {
        log.info("Getting drivers who need to upload documents");

        return driverRepository.findAllPendingDocuments().stream()
                .map(this::mapToDriverResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DriverResponse updateDriverVerificationStatus(UUID driverId, boolean isVerified, String notes) {
        log.info("Updating driver verification status: {} - {}", driverId, isVerified);

        Driver driver = driverRepository.findByIdWithUser(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        driver.setIsVerified(isVerified);
        driver.setVerificationDate(LocalDateTime.now());
        driver.setFaceVerificationStatus(isVerified ? "VERIFIED" : "FAILED");
        driver.setDocumentVerificationStatus(isVerified ? "VERIFIED" : "REJECTED");

        if (isVerified) {
            driver.setIsAvailable(true);
        } else {
            driver.setIsAvailable(false);
        }

        driverRepository.save(driver);
        log.info("Driver verification status updated: {}", driverId);

        return mapToDriverResponse(driver);
    }

    @Transactional
    public DriverResponse updateDriverAvailability(UUID driverId, boolean isAvailable) {
        log.info("Updating driver availability: {} - {}", driverId, isAvailable);

        Driver driver = driverRepository.findByIdWithUser(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        if (!driver.getIsVerified()) {
            throw new CustomExceptions.UserNotActiveException(
                    "Driver must complete document verification and face verification before going online"
            );
        }

        if (!driver.getIsDocumentsUploaded()) {
            throw new CustomExceptions.UserNotActiveException(
                    "Driver must upload all required documents before going online"
            );
        }

        driver.setIsAvailable(isAvailable);
        driverRepository.save(driver);

        log.info("Driver availability updated: {}", driverId);
        return mapToDriverResponse(driver);
    }

    @Transactional
    public DriverResponse updateDriverProfileFromOCR(UUID driverId, String extractedFirstName,
                                                     String extractedLastName, String licenseNumber,
                                                     String licenseExpiry) {
        log.info("Updating driver profile from OCR data: {}", driverId);

        Driver driver = driverRepository.findByIdWithUser(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        User user = driver.getUser();

        // Update user profile with extracted data
        if (extractedFirstName != null && !extractedFirstName.trim().isEmpty()) {
            user.setFirstName(extractedFirstName);
        }
        if (extractedLastName != null && !extractedLastName.trim().isEmpty()) {
            user.setLastName(extractedLastName);
        }

        // Update driver license info
        if (licenseNumber != null && !licenseNumber.trim().isEmpty()) {
            driver.setLicenseNumber(licenseNumber);
        }
        if (licenseExpiry != null) {
            driver.setLicenseExpiry(java.time.LocalDate.parse(licenseExpiry));
        }

        driver.setProfileExtractionStatus("COMPLETED");

        userRepository.save(user);
        driverRepository.save(driver);

        log.info("Driver profile updated from OCR: {}", driverId);
        return mapToDriverResponse(driver);
    }

    private DriverResponse mapToDriverResponse(Driver driver) {
        User user = driver.getUser();
        return DriverResponse.builder()
                .driverId(driver.getDriverId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .dateOfBirth(user.getDateOfBirth())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .isAvailable(driver.getIsAvailable())
                .isVerified(driver.getIsVerified())
                .isDocumentsUploaded(driver.getIsDocumentsUploaded())
                .faceVerificationStatus(driver.getFaceVerificationStatus())
                .documentVerificationStatus(driver.getDocumentVerificationStatus())
                .profileExtractionStatus(driver.getProfileExtractionStatus())
                .verificationDate(driver.getVerificationDate())
                .verificationProgress(driver.getVerificationProgress())
                .licenseNumber(driver.getLicenseNumber())
                .licenseExpiry(driver.getLicenseExpiry())
                .vehicleRegistration(driver.getVehicleRegistration())
                .whatsappNumber(driver.getWhatsappNumber())
                .totalEarnings(driver.getTotalEarnings())
                .totalRidesCompleted(driver.getTotalRidesCompleted())
                .rating(driver.getRating())
                .isActive(user.getIsActive())
                .isPhoneVerified(user.getIsPhoneVerified())
                .createdAt(driver.getCreatedAt())
                .build();
    }
}