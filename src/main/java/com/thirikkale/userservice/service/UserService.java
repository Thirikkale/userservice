package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.response.UserResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getUserById(UUID userId) {
        log.info("Getting user by ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found"));

        return mapToUserResponse(user);
    }

    public UserResponse getUserByPhoneNumber(String phoneNumber) {
        log.info("Getting user by phone: {}", phoneNumber);

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found"));

        return mapToUserResponse(user);
    }

    public List<UserResponse> getAllUsers() {
        log.info("Getting all users");

        return userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse deactivateUser(UUID userId) {
        log.info("Deactivating user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found"));

        user.setIsActive(false);
        userRepository.save(user);

        log.info("User deactivated: {}", userId);
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse activateUser(UUID userId) {
        log.info("Activating user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found"));

        user.setIsActive(true);
        userRepository.save(user);

        log.info("User activated: {}", userId);
        return mapToUserResponse(user);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .dateOfBirth(user.getDateOfBirth())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .emergencyContactName(user.getEmergencyContactName())
                .emergencyContactPhone(user.getEmergencyContactPhone())
                .isActive(user.getIsActive())
                .isPhoneVerified(user.getIsPhoneVerified())
                .isEmailVerified(user.getIsEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}