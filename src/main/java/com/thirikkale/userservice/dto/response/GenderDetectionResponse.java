package com.thirikkale.userservice.dto.response;

import com.thirikkale.userservice.model.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenderDetectionResponse {

    private UUID riderId;
    private Gender detectedGender;
    private Double confidence;
    private Boolean womenOnlyAccessGranted;
    private String message;
}