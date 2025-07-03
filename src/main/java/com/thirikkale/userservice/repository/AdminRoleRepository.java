package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.AdminRole;
import com.thirikkale.userservice.model.enums.AdminRoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRoleRepository extends JpaRepository<AdminRole, UUID> {

    @Query("SELECT a FROM AdminRole a JOIN FETCH a.user WHERE a.adminId = :adminId")
    Optional<AdminRole> findByIdWithUser(UUID adminId);

    // Fix this method - the parameter binding was incorrect
    @Query("SELECT a FROM AdminRole a JOIN FETCH a.user WHERE a.user.email = :emailOrPhone OR a.user.phoneNumber = :emailOrPhone")
    Optional<AdminRole> findByEmailOrPhoneNumber(@Param("emailOrPhone") String emailOrPhone);

    List<AdminRole> findByAdminRole(AdminRoleType adminRole);

    @Query("SELECT COUNT(a) FROM AdminRole a WHERE a.user.isActive = true")
    long countActiveAdmins();

    // Additional useful queries
    @Query("SELECT a FROM AdminRole a JOIN FETCH a.user WHERE a.adminRole = :roleType AND a.user.isActive = true")
    List<AdminRole> findActiveAdminsByRole(@Param("roleType") AdminRoleType roleType);

    @Query("SELECT a FROM AdminRole a JOIN FETCH a.user WHERE a.user.userId = :userId")
    Optional<AdminRole> findByUserId(@Param("userId") UUID userId);
}