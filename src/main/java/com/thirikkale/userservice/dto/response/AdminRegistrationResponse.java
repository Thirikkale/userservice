package com.thirikkale.userservice.dto.response;

import com.thirikkale.userservice.model.enums.AdminRoleType;
import com.thirikkale.userservice.model.enums.AdminStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRegistrationResponse {

    private boolean success;
    private String message;
    private UUID adminId;
    private String readableId; // Human-readable ID like A00001, A00002
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private AdminRoleType adminRole;
    private AdminStatus status;
    private Boolean emailVerified;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}