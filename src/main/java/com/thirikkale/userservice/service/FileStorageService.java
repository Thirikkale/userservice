package com.thirikkale.userservice.service;

import com.thirikkale.userservice.exception.CustomExceptions;
import com.thirikkale.userservice.model.enums.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload.dir:uploads/}")
    private String uploadDir;

    /**
     * Store driver document with proper directory structure
     */
    public String storeDriverDocument(UUID driverId, DocumentType documentType, MultipartFile file) {
        try {
            // Create directory structure: uploads/driver-documents/{driverId}/{documentType}/
            String documentTypeDir = getDocumentTypeDirectory(documentType);
            Path targetDir = Paths.get(uploadDir, "driver-documents", driverId.toString(), documentTypeDir);

            // Create directories if they don't exist
            Files.createDirectories(targetDir);

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            // Save file
            Path targetPath = targetDir.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Return relative path for database storage
            String relativePath = "/uploads/driver-documents/" + driverId + "/" + documentTypeDir + "/" + uniqueFilename;

            log.info("File stored successfully: {}", relativePath);
            log.info("Absolute path: {}", targetPath.toAbsolutePath());

            return relativePath;

        } catch (IOException e) {
            log.error("Failed to store file for driver {}: {}", driverId, e.getMessage());
            throw new CustomExceptions.FileStorageException("Failed to store file: " + e.getMessage());
        }
    }

    /**
     * Generic file storage method (for backward compatibility and general use)
     */
    public String storeFile(MultipartFile file, String directory, String subdirectory) {
        try {
            Path targetDir = Paths.get(uploadDir, directory, subdirectory);
            Files.createDirectories(targetDir);

            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            Path targetPath = targetDir.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = "/" + directory + "/" + subdirectory + "/" + uniqueFilename;
            log.info("File stored successfully: {}", relativePath);
            return relativePath;

        } catch (IOException e) {
            log.error("Failed to store file: {}", e.getMessage());
            throw new CustomExceptions.FileStorageException("Failed to store file: " + e.getMessage());
        }
    }

    /**
     * Check if file exists at the given path
     */
    public boolean fileExists(String filePath) {
        try {
            Path path = Paths.get(filePath);
            boolean exists = Files.exists(path);
            log.debug("File existence check for {}: {}", filePath, exists);
            return exists;
        } catch (Exception e) {
            log.warn("Error checking file existence for {}: {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * Get absolute path from relative URL
     */
    public String getAbsolutePath(String relativeUrl) {
        // Remove leading slash if present
        String cleanPath = relativeUrl.startsWith("/") ? relativeUrl.substring(1) : relativeUrl;
        return System.getProperty("user.dir") + "/" + cleanPath;
    }

    /**
     * Download file content as byte array
     */
    public byte[] downloadFile(String fileUrl) {
        try {
            // Convert to absolute path if it's a relative URL
            String filePath;
            if (fileUrl.startsWith("/")) {
                filePath = getAbsolutePath(fileUrl);
            } else {
                filePath = fileUrl;
            }

            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                throw new CustomExceptions.FileStorageException("File not found: " + fileUrl);
            }

            return Files.readAllBytes(path);

        } catch (IOException e) {
            log.error("Failed to download file {}: {}", fileUrl, e.getMessage());
            throw new CustomExceptions.FileStorageException("Failed to download file: " + e.getMessage());
        }
    }

    /**
     * Save multipart file to temporary location for Python processing
     */
    public Path saveToTempFile(MultipartFile file, String prefix) {
        try {
            String extension = getFileExtension(file.getOriginalFilename());
            Path tempFile = Files.createTempFile(prefix + "_" + UUID.randomUUID(), extension);

            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Temporary file created: {}", tempFile);

            return tempFile;

        } catch (IOException e) {
            log.error("Failed to create temporary file: {}", e.getMessage());
            throw new CustomExceptions.FileStorageException("Failed to create temporary file: " + e.getMessage());
        }
    }

    /**
     * Delete temporary file
     */
    public void deleteTempFile(Path tempFile) {
        try {
            if (tempFile != null && Files.exists(tempFile)) {
                Files.delete(tempFile);
                log.debug("Temporary file deleted: {}", tempFile);
            }
        } catch (IOException e) {
            log.warn("Failed to delete temporary file {}: {}", tempFile, e.getMessage());
        }
    }

    /**
     * Get directory name for document type
     */
    private String getDocumentTypeDirectory(DocumentType documentType) {
        switch (documentType) {
            case SELFIE:
                return "selfie";
            case DRIVING_LICENSE:
                return "driving_license";
            case REVENUE_LICENSE:
                return "revenue_license";
            case VEHICLE_REGISTRATION:
                return "vehicle_registration";
            case VEHICLE_INSURANCE:
                return "vehicle_insurance";
            default:
                return "other";
        }
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return ".jpg"; // Default extension
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex);
        }

        return ".jpg"; // Default extension
    }

    /**
     * Validate file type and size
     */
    public void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new CustomExceptions.InvalidFileException("File is empty");
        }

        // Check file size (10MB limit)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new CustomExceptions.InvalidFileException("File size exceeds 10MB limit");
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomExceptions.InvalidFileException("Only image files are allowed");
        }
    }
}