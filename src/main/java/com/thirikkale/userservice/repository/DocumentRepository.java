package com.thirikkale.userservice.repository;

import com.thirikkale.userservice.model.Document;
import com.thirikkale.userservice.model.enums.DocumentStatus;
import com.thirikkale.userservice.model.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByDriverId(UUID driverId);

    Optional<Document> findByDriverIdAndDocumentType(UUID driverId, DocumentType documentType);

    List<Document> findByDriverIdAndStatus(UUID driverId, DocumentStatus status);

    List<Document> findByStatus(DocumentStatus status);

    boolean existsByDriverIdAndDocumentType(UUID driverId, DocumentType documentType);

    long countByDriverIdAndStatus(UUID driverId, DocumentStatus status);
}