package com.thirikkale.userservice.dto.request;

import com.thirikkale.userservice.model.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRegistrationRequest {

    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;

    private String vehicleRegistration;

    private String vehicleModel;
    private String vehicleYear;
    private String vehicleColor;
    private String vehicleMake;
    private String insuranceCompany;
    private String insurancePolicyNumber;
}