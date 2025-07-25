package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.request.VehicleRegistrationRequest;
import com.thirikkale.userservice.dto.response.VehicleResponse;
import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.Driver;
import com.thirikkale.userservice.model.Vehicle;
import com.thirikkale.userservice.repository.DriverRepository;
import com.thirikkale.userservice.repository.VehicleRepository;
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
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;

    @Transactional
    public VehicleResponse registerVehicle(UUID driverId, VehicleRegistrationRequest request) {
        log.info("Registering new vehicle for driver: {}", driverId);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        // Check if vehicle registration already exists
        if (vehicleRepository.existsByVehicleRegistration(request.getVehicleRegistration())) {
            throw new CustomExceptions.VehicleAlreadyExistsException("Vehicle with registration " +
                    request.getVehicleRegistration() + " already exists");
        }

        Vehicle vehicle = Vehicle.builder()
                .driver(driver)
                .vehicleType(request.getVehicleType())
                .vehicleRegistration(request.getVehicleRegistration())
                .vehicleModel(request.getVehicleModel())
                .vehicleYear(request.getVehicleYear())
                .vehicleColor(request.getVehicleColor())
                .vehicleMake(request.getVehicleMake())
                .insuranceCompany(request.getInsuranceCompany())
                .insurancePolicyNumber(request.getInsurancePolicyNumber())
                .isActive(true)
                .isVerified(false)
                .isDocumentsUploaded(false)
                .verificationStatus("PENDING")
                .build();

        vehicle = vehicleRepository.save(vehicle);

        // If this is the first vehicle, make it primary
        if (driver.getPrimaryVehicle() == null) {
            driver.setPrimaryVehicle(vehicle);
            driverRepository.save(driver);
        }

        log.info("Vehicle registered successfully: {}", vehicle.getVehicleId());
        return mapToVehicleResponse(vehicle, driver.getPrimaryVehicle());
    }

    public List<VehicleResponse> getDriverVehicles(UUID driverId) {
        log.info("Getting vehicles for driver: {}", driverId);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        List<Vehicle> vehicles = vehicleRepository.findByDriverId(driverId);

        return vehicles.stream()
                .map(vehicle -> mapToVehicleResponse(vehicle, driver.getPrimaryVehicle()))
                .collect(Collectors.toList());
    }

    public VehicleResponse getVehicleById(UUID driverId, UUID vehicleId) {
        log.info("Getting vehicle {} for driver: {}", vehicleId, driverId);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        Vehicle vehicle = vehicleRepository.findByDriverIdAndVehicleId(driverId, vehicleId)
                .orElseThrow(() -> new CustomExceptions.VehicleNotFoundException("Vehicle not found"));

        return mapToVehicleResponse(vehicle, driver.getPrimaryVehicle());
    }

    @Transactional
    public VehicleResponse setPrimaryVehicle(UUID driverId, UUID vehicleId) {
        log.info("Setting primary vehicle {} for driver: {}", vehicleId, driverId);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        Vehicle vehicle = vehicleRepository.findByDriverIdAndVehicleId(driverId, vehicleId)
                .orElseThrow(() -> new CustomExceptions.VehicleNotFoundException("Vehicle not found"));

        // Only verified vehicles can be set as primary
        if (!vehicle.isFullyVerified()) {
            throw new CustomExceptions.VehicleNotVerifiedException("Only verified vehicles can be set as primary");
        }

        driver.setPrimaryVehicle(vehicle);
        driverRepository.save(driver);

        log.info("Primary vehicle set successfully for driver: {}", driverId);
        return mapToVehicleResponse(vehicle, vehicle);
    }

    @Transactional
    public VehicleResponse deactivateVehicle(UUID driverId, UUID vehicleId) {
        log.info("Deactivating vehicle {} for driver: {}", vehicleId, driverId);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        Vehicle vehicle = vehicleRepository.findByDriverIdAndVehicleId(driverId, vehicleId)
                .orElseThrow(() -> new CustomExceptions.VehicleNotFoundException("Vehicle not found"));

        vehicle.setIsActive(false);
        vehicle = vehicleRepository.save(vehicle);

        // If this was the primary vehicle, clear it
        if (driver.getPrimaryVehicle() != null &&
                driver.getPrimaryVehicle().getVehicleId().equals(vehicleId)) {
            driver.setPrimaryVehicle(null);
            driverRepository.save(driver);
        }

        log.info("Vehicle deactivated successfully: {}", vehicleId);
        return mapToVehicleResponse(vehicle, driver.getPrimaryVehicle());
    }

    private VehicleResponse mapToVehicleResponse(Vehicle vehicle, Vehicle primaryVehicle) {
        return VehicleResponse.builder()
                .vehicleId(vehicle.getVehicleId())
                .driverId(vehicle.getDriver().getDriverId())
                .vehicleType(vehicle.getVehicleType())
                .vehicleRegistration(vehicle.getVehicleRegistration())
                .vehicleModel(vehicle.getVehicleModel())
                .vehicleYear(vehicle.getVehicleYear())
                .vehicleColor(vehicle.getVehicleColor())
                .vehicleMake(vehicle.getVehicleMake())
                .isActive(vehicle.getIsActive())
                .isVerified(vehicle.getIsVerified())
                .isDocumentsUploaded(vehicle.getIsDocumentsUploaded())
                .verificationStatus(vehicle.getVerificationStatus())
                .verificationProgress(vehicle.getVerificationProgress())
                .revenueLicenseUrl(vehicle.getRevenueLicenseUrl())
                .vehicleRegistrationUrl(vehicle.getVehicleRegistrationUrl())
                .vehicleInsuranceUrl(vehicle.getVehicleInsuranceUrl())
                .insuranceCompany(vehicle.getInsuranceCompany())
                .insurancePolicyNumber(vehicle.getInsurancePolicyNumber())
                .insuranceExpiry(vehicle.getInsuranceExpiry())
                .revenueLicenseExpiry(vehicle.getRevenueLicenseExpiry())
                .isPrimary(primaryVehicle != null && primaryVehicle.getVehicleId().equals(vehicle.getVehicleId()))
                .createdAt(vehicle.getCreatedAt())
                .build();
    }
}