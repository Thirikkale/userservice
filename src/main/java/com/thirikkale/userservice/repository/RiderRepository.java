package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.Rider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiderRepository extends JpaRepository<Rider, UUID> {

    @Query("SELECT r FROM Rider r JOIN FETCH r.user WHERE r.riderId = :riderId")
    Optional<Rider> findByIdWithUser(@Param("riderId") UUID riderId);

    @Query("SELECT r FROM Rider r JOIN FETCH r.user WHERE r.user.phoneNumber = :phoneNumber")
    Optional<Rider> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    @Query("SELECT COUNT(r) > 0 FROM Rider r WHERE r.user.phoneNumber = :phoneNumber")
    boolean existsByUser_PhoneNumber(@Param("phoneNumber") String phoneNumber);

    @Query("SELECT COUNT(r) FROM Rider r WHERE r.user.isActive = true")
    long countActiveRiders();

    @Query("SELECT COUNT(r) FROM Rider r WHERE r.genderVerified = true")
    long countGenderVerifiedRiders();

    @Query("SELECT COUNT(r) FROM Rider r WHERE r.womenOnlyAccess = true")
    long countWomenOnlyAccessRiders();
}