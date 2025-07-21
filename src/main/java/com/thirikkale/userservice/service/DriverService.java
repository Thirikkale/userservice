package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.request.DriverProfileUpdateRequest;
import com.thirikkale.userservice.dto.request.DriverRegistrationRequest;
import com.thirikkale.userservice.dto.request.VehicleTypeUpdateRequest;
import com.thirikkale.userservice.dto.response.AuthResponse;
import com.thirikkale.userservice.dto.response.DriverResponse;
import com.thirikkale.userservice.model.enums.VehicleType;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
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
    private final FileStorageService fileStorageService;

    // Add date formatters for parsing
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    //added lately
    @Transactional
    public DriverResponse updateDriverVehicleType(UUID driverId, VehicleTypeUpdateRequest request) {
        log.info("Updating vehicle type for driver: {} to {}", driverId, request.getVehicleType());

        Driver driver = driverRepository.findByIdWithUser(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        // Update vehicle type
        driver.setVehicleType(request.getVehicleType());

        driver = driverRepository.save(driver);

        log.info("Vehicle type updated successfully for driver: {}", driverId);
        return mapToDriverResponse(driver);
    }

    // NEW: Add the completeDriverProfileSetup method
    @Transactional
    public AuthResponse completeDriverProfileSetup(UUID driverId, String firstName, String lastName,
                                                   String whatsappNumber, VehicleType vehicleType) {
        log.info("Completing driver profile setup for: {}", driverId);

        Driver driver = driverRepository.findByIdWithUser(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        User user = driver.getUser();

        // Update user information
        user.setFirstName(firstName);
        user.setLastName(lastName);

        // Update driver-specific information
        if (whatsappNumber != null && !whatsappNumber.trim().isEmpty()) {
            driver.setWhatsappNumber(whatsappNumber.trim());
        }

        // NEW: Set vehicle type
        if (vehicleType != null) {
            driver.setVehicleType(vehicleType);
            log.info("Vehicle type set to: {}", vehicleType);
        }

        // Save both entities
        userRepository.save(user);
        driver = driverRepository.save(driver);

        // Generate new tokens with updated profile
        String accessToken = jwtService.generateAccessToken(driverId, user.getPhoneNumber(), "DRIVER");
        String refreshToken = jwtService.generateRefreshToken(driverId, user.getPhoneNumber());

        log.info("Driver profile setup completed for: {}", driverId);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userId(driverId)
                .userType("DRIVER")
                .phoneNumber(user.getPhoneNumber())
                .firstName(firstName)
                .lastName(lastName)
                .email(user.getEmail())
                .isActive(user.getIsActive())
                .isVerified(driver.getIsVerified())
                .loginTime(LocalDateTime.now())
                .isNewRegistration(false)
                .registrationMessage("Profile setup completed successfully!")
                .nextStep(vehicleType != null ? "Start uploading documents" : "Select your vehicle type and upload documents")
                .build();
    }


    @Transactional
    public AuthResponse registerDriver(DriverRegistrationRequest request) {
        log.info("Registering driver with Firebase token - simplified flow");

        try {
            // 1. Verify Firebase token
            FirebaseUserInfo firebaseUserInfo = firebaseAuthService.extractUserInfo(request.getFirebaseIdToken());
            if (!firebaseUserInfo.isPhoneVerified()) {
                throw new CustomExceptions.InvalidTokenException("Phone number not verified in Firebase");
            }

            // 2. Format phone number
            String formattedPhone = phoneNumberValidator.formatToE164(firebaseUserInfo.getPhoneNumber());
            log.info("Firebase token verified for phone: {}", formattedPhone);

            // 3. Check if driver already exists
            if (driverRepository.existsByUser_PhoneNumber(formattedPhone)) {
                log.warn("Driver already exists for phone: {}", formattedPhone);
                throw new CustomExceptions.UserAlreadyExistsException("Driver already registered with this phone number");
            }

            // 4. Create or get user - NO NAMES IN REQUEST ANYMORE
            User user = getOrCreateUser(formattedPhone, firebaseUserInfo);

            // 5. Create driver profile
            Driver driver = createDriverProfile(user);

            log.info("Successfully registered driver: {}", driver.getDriverId());
            return createDriverAuthResponse(driver, formattedPhone);

        } catch (Exception e) {
            log.error("Driver registration failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    // FIXED: Remove request parameter since names are no longer in the request
    private User getOrCreateUser(String formattedPhone, FirebaseUserInfo firebaseUserInfo) {
        Optional<User> existingUser = userRepository.findByPhoneNumber(formattedPhone);

        if (existingUser.isPresent()) {
            log.info("Found existing user for phone: {}", formattedPhone);
            User user = existingUser.get();

            // Update user with Firebase info if needed
            user.setIsPhoneVerified(true);
            user.setLastLoginAt(LocalDateTime.now());
            if (user.getEmail() == null && firebaseUserInfo.getEmail() != null) {
                user.setEmail(firebaseUserInfo.getEmail());
            }
            if (user.getProfilePhotoUrl() == null && firebaseUserInfo.getPicture() != null) {
                user.setProfilePhotoUrl(firebaseUserInfo.getPicture());
            }

            return userRepository.save(user);
        } else {
            log.info("Creating new user for phone: {}", formattedPhone);

            // FIXED: Create user with placeholder names that will be updated later
            User newUser = User.builder()
                    .phoneNumber(formattedPhone)
                    .firstName("Driver") // Placeholder - will be updated in profile completion
                    .lastName("User")    // Placeholder - will be updated in profile completion
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

    private Driver createDriverProfile(User user) {
        Driver driver = Driver.builder()
                .driverId(user.getUserId())
                .user(user)
                .isAvailable(false)
                .isVerified(false)
                .isDocumentsUploaded(false)
                .faceVerificationStatus("PENDING")
                .documentVerificationStatus("PENDING")
                .profileExtractionStatus("PENDING")
                .faceVerificationAttempts(0)
                .totalEarnings(BigDecimal.ZERO)
                .totalRidesCompleted(0)
                .rating(BigDecimal.ZERO)
                .build();

        return driverRepository.save(driver);
    }

    private AuthResponse createDriverAuthResponse(Driver driver, String phoneNumber) {
        String accessToken = jwtService.generateAccessToken(
                driver.getDriverId(),
                phoneNumber,
                "DRIVER"
        );

        String refreshToken = jwtService.generateRefreshToken(
                driver.getDriverId(),
                phoneNumber
        );

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userId(driver.getDriverId())
                .userType("DRIVER")
                .phoneNumber(phoneNumber)
                .firstName(driver.getUser().getFirstName())
                .lastName(driver.getUser().getLastName())
                .email(driver.getUser().getEmail())
                .isActive(driver.getUser().getIsActive())
                .isVerified(driver.getUser().getIsPhoneVerified())
                .loginTime(LocalDateTime.now())
                .isNewRegistration(true) // This is a new registration
                .registrationMessage("Registration successful! Please complete your profile.")
                .nextStep("COMPLETE_PROFILE") // Signal that profile completion is needed
                .build();
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
            log.info("Driver verified successfully: {}", driverId);
        } else {
            log.info("Driver verification rejected: {} - Notes: {}", driverId, notes);
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
            throw new CustomExceptions.UserNotActiveException("Driver must be verified before going online");
        }

        driver.setIsAvailable(isAvailable);
        driverRepository.save(driver);

        log.info("Driver availability updated: {} - {}", driverId, isAvailable);
        return mapToDriverResponse(driver);
    }

    @Transactional
    public DriverResponse updateDriverProfileFromOCR(UUID driverId, String extractedFirstName,
                                                     String extractedLastName, String licenseNumber,
                                                     String licenseExpiry) {
        log.info("Updating driver profile from OCR - Driver: {}", driverId);

        Driver driver = driverRepository.findByIdWithUser(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        User user = driver.getUser();

        // FIXED: Only update names if they are currently placeholders
        if ("Driver".equals(user.getFirstName()) && extractedFirstName != null && !extractedFirstName.trim().isEmpty()) {
            user.setFirstName(extractedFirstName.trim());
            log.info("Updated first name from OCR: {}", extractedFirstName);
        }

        if ("User".equals(user.getLastName()) && extractedLastName != null && !extractedLastName.trim().isEmpty()) {
            user.setLastName(extractedLastName.trim());
            log.info("Updated last name from OCR: {}", extractedLastName);
        }

        // Update driver-specific fields
        if (licenseNumber != null && !licenseNumber.trim().isEmpty()) {
            driver.setLicenseNumber(licenseNumber.trim());
            log.info("Updated license number: {}", licenseNumber);
        }

        if (licenseExpiry != null && !licenseExpiry.trim().isEmpty()) {
            LocalDate expiryDate = parseDate(licenseExpiry);
            if (expiryDate != null) {
                driver.setLicenseExpiry(expiryDate);
                log.info("Updated license expiry: {}", expiryDate);
            }
        }

        // Update extraction status
        driver.setProfileExtractionStatus("COMPLETED");

        // Save both entities
        userRepository.save(user);
        driver = driverRepository.save(driver);

        log.info("Driver profile updated from OCR successfully: {}", driverId);
        return mapToDriverResponse(driver);
    }

    /**
     * FIXED: Parse date with multiple formats - handles null cases
     */
    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }

        String cleanDate = dateString.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(cleanDate, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        log.warn("Could not parse date string: {}", dateString);
        return null;
    }

    private DriverResponse mapToDriverResponse(Driver driver) {
        User user = driver.getUser();
        // Verify file existence for debugging
        if (driver.getSelfieUrl() != null) {
            boolean exists = fileStorageService.fileExists(driver.getSelfieUrl());
            log.info("Selfie file exists: {} for path: {}", exists, driver.getSelfieUrl());
        }

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
                .vehicleType(driver.getVehicleType()) // Vehicle type included
                .whatsappNumber(driver.getWhatsappNumber())
                .totalEarnings(driver.getTotalEarnings())
                .totalRidesCompleted(driver.getTotalRidesCompleted())
                .rating(driver.getRating())
                .isActive(user.getIsActive())
                .isPhoneVerified(user.getIsPhoneVerified())
                .createdAt(driver.getCreatedAt())
                .selfieUrl(driver.getSelfieUrl())
                .drivingLicenseUrl(driver.getDrivingLicenseUrl())
                .revenueLicenseUrl(driver.getRevenueLicenseUrl())
                .vehicleRegistrationUrl(driver.getVehicleRegistrationUrl())
                .vehicleInsuranceUrl(driver.getVehicleInsuranceUrl())
                .faceMatchScore(driver.getFaceMatchScore())
                .faceVerificationAttempts(driver.getFaceVerificationAttempts())
                .build();
    }

    //newly added
    @Transactional
    public DriverResponse updateDriverProfile(UUID driverId, DriverProfileUpdateRequest request) {
        log.info("Updating driver profile: {}", driverId);

        Driver driver = driverRepository.findByIdWithUser(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        User user = driver.getUser();

        // Update user information
        if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) {
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

        // Update driver-specific information
        if (request.getWhatsappNumber() != null && !request.getWhatsappNumber().trim().isEmpty()) {
            driver.setWhatsappNumber(request.getWhatsappNumber().trim());
        }

        // Save changes
        userRepository.save(user);
        driver = driverRepository.save(driver);

        log.info("Driver profile updated successfully: {}", driverId);
        return mapToDriverResponse(driver);
    }
}