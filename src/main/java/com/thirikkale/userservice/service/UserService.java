package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.response.UserResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.User;
import com.thirikkale.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Cacheable(value = "users", key = "#userId")
    public UserResponse getUserById(UUID userId) {
        log.info("Fetching user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found with ID: " + userId));

        return mapToUserResponse(user);
    }

    @Cacheable(value = "users", key = "#email")
    public UserResponse getUserByEmail(String email) {
        log.info("Fetching user with email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("User not found with email: " + email));

        return mapToUserResponse(user);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dateOfBirth(user.getDateOfBirth())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .emergencyContactName(user.getEmergencyContactName())
                .emergencyContactPhone(user.getEmergencyContactPhone())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}