package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.driverId = :driverId")
    Optional<Driver> findByIdWithUser(@Param("driverId") UUID driverId);

    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.user.phoneNumber = :phoneNumber")
    Optional<Driver> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    @Query("SELECT COUNT(d) > 0 FROM Driver d WHERE d.user.phoneNumber = :phoneNumber")
    boolean existsByUser_PhoneNumber(@Param("phoneNumber") String phoneNumber);

    // ENHANCED: Safety check for existing driver by user ID
    @Query("SELECT COUNT(d) > 0 FROM Driver d WHERE d.driverId = :userId")
    boolean existsByDriverId(@Param("userId") UUID userId);

    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.isAvailable = true AND d.isVerified = true AND d.user.isActive = true")
    List<Driver> findAllAvailableAndVerified();

    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.isVerified = false AND d.isDocumentsUploaded = true")
    List<Driver> findAllPendingVerification();

    // New query for drivers who haven't uploaded documents yet
    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.isDocumentsUploaded = false AND d.user.isActive = true")
    List<Driver> findAllPendingDocuments();

    // Query for drivers pending face verification
    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.faceVerificationStatus IN ('PENDING', 'IN_PROGRESS') AND d.isDocumentsUploaded = true")
    List<Driver> findAllPendingFaceVerification();

    // Query for drivers pending manual review
    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.faceVerificationStatus = 'MANUAL_REVIEW'")
    List<Driver> findAllPendingManualReview();

    // Query for drivers with profile extraction pending
    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.profileExtractionStatus IN ('PENDING', 'IN_PROGRESS')")
    List<Driver> findAllPendingProfileExtraction();

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.user.isActive = true")
    long countActiveDrivers();

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.isVerified = true")
    long countVerifiedDrivers();

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.isDocumentsUploaded = false")
    long countDriversPendingDocuments();

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.faceVerificationStatus = 'MANUAL_REVIEW'")
    long countDriversPendingManualReview();

    boolean existsByLicenseNumber(String licenseNumber);

    boolean existsByVehicleRegistration(String vehicleRegistration);
}