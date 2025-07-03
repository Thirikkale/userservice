package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List; // Add this import
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.email = :emailOrPhone OR u.phoneNumber = :emailOrPhone")
    Optional<User> findByEmailOrPhoneNumber(@Param("emailOrPhone") String emailOrPhone);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Driver d WHERE d.user.userId = :userId")
    boolean existsDriverByUserId(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Rider r WHERE r.user.userId = :userId")
    boolean existsRiderByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.isPhoneVerified = true")
    long countVerifiedUsers();

    @Query("SELECT u FROM User u WHERE u.isActive = false")
    List<User> findInactiveUsers();

    @Query("SELECT u FROM User u WHERE u.isPhoneVerified = false")
    List<User> findUnverifiedUsers();
}