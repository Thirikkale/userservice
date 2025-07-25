package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.Vehicle;
import com.thirikkale.userservice.model.enums.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    @Query("SELECT v FROM Vehicle v WHERE v.driver.driverId = :driverId AND v.isActive = true")
    List<Vehicle> findByDriverId(@Param("driverId") UUID driverId);

    @Query("SELECT v FROM Vehicle v WHERE v.driver.driverId = :driverId AND v.vehicleId = :vehicleId AND v.isActive = true")
    Optional<Vehicle> findByDriverIdAndVehicleId(@Param("driverId") UUID driverId, @Param("vehicleId") UUID vehicleId);

    @Query("SELECT v FROM Vehicle v WHERE v.vehicleRegistration = :registration")
    Optional<Vehicle> findByVehicleRegistration(@Param("registration") String registration);

    boolean existsByVehicleRegistration(String vehicleRegistration);

    @Query("SELECT v FROM Vehicle v WHERE v.driver.driverId = :driverId AND v.vehicleType = :vehicleType AND v.isActive = true")
    List<Vehicle> findByDriverIdAndVehicleType(@Param("driverId") UUID driverId, @Param("vehicleType") VehicleType vehicleType);

    @Query("SELECT v FROM Vehicle v WHERE v.isVerified = true AND v.isActive = true")
    List<Vehicle> findAllVerifiedVehicles();

    @Query("SELECT v FROM Vehicle v WHERE v.isDocumentsUploaded = false AND v.isActive = true")
    List<Vehicle> findAllPendingDocuments();

    @Query("SELECT COUNT(v) FROM Vehicle v WHERE v.driver.driverId = :driverId AND v.isActive = true")
    long countActiveVehiclesByDriver(@Param("driverId") UUID driverId);

    @Query("SELECT COUNT(v) FROM Vehicle v WHERE v.driver.driverId = :driverId AND v.isVerified = true AND v.isActive = true")
    long countVerifiedVehiclesByDriver(@Param("driverId") UUID driverId);
}