package com.thirikkale.userservice.service;

import com.thirikkale.userservice.dto.request.VehicleRegistrationRequest;
import com.thirikkale.userservice.dto.request.VehicleTypeUpdateRequest;
import com.thirikkale.userservice.dto.response.VehicleResponse;
import com.thirikkale.userservice.dto.response.AdminVehicleResponse;
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
                .readableId(vehicle.getReadableId()) // V00001
                .driverId(vehicle.getDriver().getDriverId())
                .driverReadableId(vehicle.getDriver().getReadableId()) // D00001
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

    @Transactional
    public VehicleResponse updateVehicleType(UUID driverId, UUID vehicleId, VehicleTypeUpdateRequest request) {
        log.info("Updating vehicle type for vehicle {} of driver: {}", vehicleId, driverId);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        Vehicle vehicle = vehicleRepository.findByDriverIdAndVehicleId(driverId, vehicleId)
                .orElseThrow(() -> new CustomExceptions.VehicleNotFoundException("Vehicle not found"));

        // Update vehicle type and related fields
        vehicle.setVehicleType(request.getVehicleType());

        if (request.getVehicleModel() != null) {
            vehicle.setVehicleModel(request.getVehicleModel());
        }
        if (request.getVehicleYear() != null) {
            vehicle.setVehicleYear(request.getVehicleYear());
        }
        if (request.getVehicleColor() != null) {
            vehicle.setVehicleColor(request.getVehicleColor());
        }

        vehicle = vehicleRepository.save(vehicle);

        log.info("Vehicle type updated successfully for vehicle: {} - new type: {}",
                vehicleId, request.getVehicleType());

        return mapToVehicleResponse(vehicle, driver.getPrimaryVehicle());
    }

    @Transactional
    public VehicleResponse updatePrimaryVehicleType(UUID driverId, VehicleTypeUpdateRequest request) {
        log.info("Updating primary vehicle type for driver: {}", driverId);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new CustomExceptions.UserNotFoundException("Driver not found"));

        if (driver.getPrimaryVehicle() == null) {
            throw new CustomExceptions.VehicleNotFoundException("No primary vehicle found for driver");
        }

        Vehicle primaryVehicle = driver.getPrimaryVehicle();

        // Update vehicle type and related fields
        primaryVehicle.setVehicleType(request.getVehicleType());

        if (request.getVehicleModel() != null) {
            primaryVehicle.setVehicleModel(request.getVehicleModel());
        }
        if (request.getVehicleYear() != null) {
            primaryVehicle.setVehicleYear(request.getVehicleYear());
        }
        if (request.getVehicleColor() != null) {
            primaryVehicle.setVehicleColor(request.getVehicleColor());
        }

        primaryVehicle = vehicleRepository.save(primaryVehicle);

        log.info("Primary vehicle type updated successfully for driver: {} - new type: {}",
                driverId, request.getVehicleType());

        return mapToVehicleResponse(primaryVehicle, primaryVehicle);
    }

    // ==================== Admin Vehicle Management ====================

    /**
     * Get all vehicles (Admin only)
     */
    @Transactional(readOnly = true)
    public List<AdminVehicleResponse> getAllVehicles() {
        log.info("Admin: Fetching all vehicles");
        List<Vehicle> vehicles = vehicleRepository.findAllWithDriver();
        return vehicles.stream()
                .map(vehicle -> mapToAdminVehicleResponse(vehicle))
                .collect(Collectors.toList());
    }

    /**
     * Get vehicle by ID for admin (no driver validation)
     */
    @Transactional(readOnly = true)
    public AdminVehicleResponse getVehicleByIdAdmin(UUID vehicleId) {
        log.info("Admin: Fetching vehicle by ID: {}", vehicleId);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new CustomExceptions.VehicleNotFoundException("Vehicle not found"));
        return mapToAdminVehicleResponse(vehicle);
    }

    /**
     * Update vehicle document verification status
     */
    @Transactional
    public AdminVehicleResponse updateDocumentStatus(UUID vehicleId, String documentType, String status) {
        log.info("Admin: Updating vehicle document status: {}, type: {}, status: {}",
                vehicleId, documentType, status);

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new CustomExceptions.VehicleNotFoundException("Vehicle not found"));

        switch (documentType.toLowerCase()) {
            case "revenuelicense":
                vehicle.setRevenueLicenseVerificationStatus(status);
                break;
            case "vehicleregistration":
                vehicle.setVehicleRegistrationVerificationStatus(status);
                break;
            case "vehicleinsurance":
                vehicle.setVehicleInsuranceVerificationStatus(status);
                break;
            default:
                throw new IllegalArgumentException("Unknown document type: " + documentType);
        }

        // Update overall verification progress
        updateVerificationProgress(vehicle);

        vehicleRepository.save(vehicle);
        log.info("Document status updated successfully for vehicle: {}", vehicleId);

        return mapToAdminVehicleResponse(vehicle);
    }

    /**
     * Helper method to map Vehicle to AdminVehicleResponse with driver name
     */
    private AdminVehicleResponse mapToAdminVehicleResponse(Vehicle vehicle) {
        String driverName = "Unknown";
        String driverReadableId = null;
        if (vehicle.getDriver() != null && vehicle.getDriver().getUser() != null) {
            driverName = vehicle.getDriver().getUser().getFirstName() + " " +
                    vehicle.getDriver().getUser().getLastName();
            driverReadableId = vehicle.getDriver().getReadableId(); // D00001
        }

        return AdminVehicleResponse.builder()
                .vehicleId(vehicle.getVehicleId().toString())
                .readableId(vehicle.getReadableId()) // V00001
                .driverId(vehicle.getDriver() != null ? vehicle.getDriver().getDriverId().toString() : null)
                .driverReadableId(driverReadableId) // D00001
                .driverName(driverName)
                .vehicleType(vehicle.getVehicleType() != null ? vehicle.getVehicleType().name() : null)
                .vehicleRegistration(vehicle.getVehicleRegistration())
                .vehicleModel(vehicle.getVehicleModel())
                .vehicleYear(vehicle.getVehicleYear())
                .vehicleColor(vehicle.getVehicleColor())
                .vehicleMake(vehicle.getVehicleMake())
                .isActive(vehicle.getIsActive())
                .isVerified(vehicle.getIsVerified())
                .isDocumentsUploaded(vehicle.getIsDocumentsUploaded())
                .verificationStatus(vehicle.getVerificationStatus())
                .revenueLicenseUrl(vehicle.getRevenueLicenseUrl())
                .vehicleRegistrationUrl(vehicle.getVehicleRegistrationUrl())
                .vehicleInsuranceUrl(vehicle.getVehicleInsuranceUrl())
                .revenueLicenseVerificationStatus(vehicle.getRevenueLicenseVerificationStatus())
                .vehicleRegistrationVerificationStatus(vehicle.getVehicleRegistrationVerificationStatus())
                .vehicleInsuranceVerificationStatus(vehicle.getVehicleInsuranceVerificationStatus())
                .insuranceCompany(vehicle.getInsuranceCompany())
                .insurancePolicyNumber(vehicle.getInsurancePolicyNumber())
                .insuranceExpiry(vehicle.getInsuranceExpiry() != null ? vehicle.getInsuranceExpiry().toString() : null)
                .revenueLicenseExpiry(
                        vehicle.getRevenueLicenseExpiry() != null ? vehicle.getRevenueLicenseExpiry().toString() : null)
                .createdAt(vehicle.getCreatedAt() != null ? vehicle.getCreatedAt().toString() : null)
                .build();
    }

    /**
     * Helper method to map Vehicle to VehicleResponse with driver name (deprecated
     * - keeping for backward compatibility)
     */
    @Deprecated
    private VehicleResponse mapToVehicleResponseWithDriverName(Vehicle vehicle) {
        return VehicleResponse.builder()
                .vehicleId(vehicle.getVehicleId())
                .readableId(vehicle.getReadableId()) // V00001
                .driverId(vehicle.getDriver().getDriverId())
                .driverReadableId(vehicle.getDriver().getReadableId()) // D00001
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
                .isPrimary(vehicle.getDriver().getPrimaryVehicle() != null &&
                        vehicle.getDriver().getPrimaryVehicle().getVehicleId().equals(vehicle.getVehicleId()))
                .createdAt(vehicle.getCreatedAt())
                .build();
    }

    /**
     * Update verification progress based on document statuses
     */
    private void updateVerificationProgress(Vehicle vehicle) {
        int totalDocs = 3;
        int approvedDocs = 0;

        if ("APPROVED".equals(vehicle.getRevenueLicenseVerificationStatus()))
            approvedDocs++;
        if ("APPROVED".equals(vehicle.getVehicleRegistrationVerificationStatus()))
            approvedDocs++;
        if ("APPROVED".equals(vehicle.getVehicleInsuranceVerificationStatus()))
            approvedDocs++;

        int progress = (approvedDocs * 100) / totalDocs;

        // Update overall verification status
        if (progress == 100) {
            vehicle.setIsVerified(true);
            vehicle.setVerificationStatus("VERIFIED");
        } else if (approvedDocs > 0) {
            vehicle.setVerificationStatus("PENDING");
        }
    }
}
