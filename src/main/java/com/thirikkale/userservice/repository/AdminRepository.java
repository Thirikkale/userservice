package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.Admin;
import com.thirikkale.userservice.model.enums.AdminRoleType;
import com.thirikkale.userservice.model.enums.AdminStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRepository extends JpaRepository<Admin, UUID> {

    /**
     * Find admin by email
     */
    Optional<Admin> findByEmail(String email);

    /**
     * Find admin by phone number
     */
    Optional<Admin> findByPhoneNumber(String phoneNumber);

    /**
     * Find admin by email or phone number
     */
    @Query("SELECT a FROM Admin a WHERE a.email = :emailOrPhone OR a.phoneNumber = :emailOrPhone")
    Optional<Admin> findByEmailOrPhoneNumber(@Param("emailOrPhone") String emailOrPhone);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Check if phone number exists
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Find admin by email verification token
     */
    Optional<Admin> findByEmailVerificationToken(String token);

    /**
     * Find all admins by role
     */
    List<Admin> findByAdminRole(AdminRoleType adminRole);

    /**
     * Find all admins by status
     */
    List<Admin> findByStatus(AdminStatus status);

    /**
     * Find all active/online admins
     */
    @Query("SELECT a FROM Admin a WHERE a.status IN ('ACTIVATED', 'ONLINE')")
    List<Admin> findAllActiveAdmins();

    /**
     * Find all online admins
     */
    @Query("SELECT a FROM Admin a WHERE a.status = 'ONLINE'")
    List<Admin> findAllOnlineAdmins();

    /**
     * Find all pending activation admins
     */
    @Query("SELECT a FROM Admin a WHERE a.status = 'PENDING_ACTIVATION' ORDER BY a.createdAt DESC")
    List<Admin> findPendingActivationAdmins();

    /**
     * Find all admins with pending approval
     */
    List<Admin> findByStatusOrderByCreatedAtDesc(AdminStatus status);

    /**
     * Count admins by role
     */
    long countByAdminRole(AdminRoleType adminRole);

    /**
     * Count admins by status
     */
    long countByStatus(AdminStatus status);

    /**
     * Find admins who haven't logged in since a specific date
     */
    @Query("SELECT a FROM Admin a WHERE a.lastLogin IS NULL OR a.lastLogin < :since")
    List<Admin> findAdminsNotLoggedInSince(@Param("since") LocalDateTime since);

    /**
     * Find all admins ordered by created date
     */
    List<Admin> findAllByOrderByCreatedAtDesc();

    /**
     * Count total active admins (ACTIVATED or ONLINE)
     */
    @Query("SELECT COUNT(a) FROM Admin a WHERE a.status IN ('ACTIVATED', 'ONLINE')")
    long countActiveAdmins();
}
