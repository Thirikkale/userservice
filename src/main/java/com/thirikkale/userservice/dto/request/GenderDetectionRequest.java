package com.thirikkale.userservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
public class GenderDetectionRequest {

    @NotNull(message = "Rider ID is required")
    private UUID riderId;

    @NotNull(message = "Selfie file is required")
    private MultipartFile selfieFile;
}