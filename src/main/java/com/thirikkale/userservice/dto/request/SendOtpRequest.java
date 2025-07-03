package com.thirikkale.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendOtpRequest {

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
}