package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.Rider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiderRepository extends JpaRepository<Rider, UUID> {

    @Query("SELECT r FROM Rider r JOIN FETCH r.user WHERE r.riderId = :riderId")
    Optional<Rider> findByIdWithUser(UUID riderId);
}