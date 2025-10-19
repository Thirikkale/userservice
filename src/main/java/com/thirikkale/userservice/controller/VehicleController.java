package com.thirikkale.userservice.controller;

import com.thirikkale.userservice.dto.response.AdminVehicleResponse;
import com.thirikkale.userservice.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Vehicle Management", description = "APIs for vehicle management (Admin access)")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    @Operation(summary = "Get all vehicles", description = "Admin: Get all vehicles in the system")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<List<AdminVehicleResponse>> getAllVehicles() {
        log.info("Get all vehicles request");
        List<AdminVehicleResponse> vehicles = vehicleService.getAllVehicles();
        return ResponseEntity.ok(vehicles);
    }

    @GetMapping("/{vehicleId}")
    @Operation(summary = "Get vehicle by ID", description = "Admin: Get vehicle details by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT', 'DRIVER')")
    public ResponseEntity<AdminVehicleResponse> getVehicleById(@PathVariable UUID vehicleId) {
        log.info("Get vehicle by ID: {}", vehicleId);
        AdminVehicleResponse vehicle = vehicleService.getVehicleByIdAdmin(vehicleId);
        return ResponseEntity.ok(vehicle);
    }

    @PutMapping("/{vehicleId}/documents/{documentType}/status")
    @Operation(summary = "Update vehicle document verification status", description = "Admin: Update document verification status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER_SUPPORT_AGENT')")
    public ResponseEntity<AdminVehicleResponse> updateDocumentStatus(
            @PathVariable UUID vehicleId,
            @PathVariable String documentType,
            @RequestParam String status) {
        log.info("Update vehicle document status: {}, type: {}, status: {}",
                vehicleId, documentType, status);
        AdminVehicleResponse vehicle = vehicleService.updateDocumentStatus(vehicleId, documentType, status);
        return ResponseEntity.ok(vehicle);
    }
}
