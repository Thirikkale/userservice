package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRoleRepository extends JpaRepository<AdminRole, UUID> {

    List<AdminRole> findByAdminRole(String adminRole);

    @Query("SELECT a FROM AdminRole a JOIN FETCH a.user WHERE a.adminId = :adminId")
    Optional<AdminRole> findByIdWithUser(UUID adminId);
}