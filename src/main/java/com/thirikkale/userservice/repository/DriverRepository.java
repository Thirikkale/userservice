package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findByIsAvailable(Boolean isAvailable);

    List<Driver> findByIsVerified(Boolean isVerified);

    Optional<Driver> findByLicenseNumber(String licenseNumber);

    Optional<Driver> findByVehicleRegistration(String vehicleRegistration);

    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.driverId = :driverId")
    Optional<Driver> findByIdWithUser(UUID driverId);
}