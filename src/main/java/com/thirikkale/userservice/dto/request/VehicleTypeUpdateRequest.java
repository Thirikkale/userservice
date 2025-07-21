package com.thirikkale.userservice.dto.request;

import com.thirikkale.userservice.model.enums.VehicleType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleTypeUpdateRequest {

    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;

    private String vehicleModel;
    private String vehicleYear;
    private String vehicleColor;
    private String additionalNotes;
}