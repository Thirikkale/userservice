package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.IdSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Repository for managing ID sequences
 */
@Repository
public interface IdSequenceRepository extends JpaRepository<IdSequence, String> {

    /**
     * Find ID sequence by entity name with pessimistic write lock
     * This ensures thread-safe ID generation
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM IdSequence s WHERE s.entityName = :entityName")
    Optional<IdSequence> findByEntityNameWithLock(@Param("entityName") String entityName);
}
