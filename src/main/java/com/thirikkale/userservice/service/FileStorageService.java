package com.thirikkale.userservice.service;

import com.thirikkale.userservice.exception.CustomExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Value("${file.upload.max-size}")
    private long maxFileSize;

    public String storeFile(MultipartFile file, String category, String userId) {
        // Validate file
        validateFile(file);

        // Clean filename
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(originalFileName);
        String newFileName = UUID.randomUUID().toString() + "." + fileExtension;

        try {
            // Create directory structure: uploads/category/userId/filename
            Path uploadPath = Paths.get(uploadDir, category, userId);
            Files.createDirectories(uploadPath);

            // Store file
            Path filePath = uploadPath.resolve(newFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = String.format("/%s/%s/%s/%s", uploadDir, category, userId, newFileName);
            log.info("File stored successfully: {}", fileUrl);

            return fileUrl;

        } catch (IOException e) {
            log.error("Failed to store file: {}", e.getMessage());
            throw new CustomExceptions.DocumentUploadException("Failed to store file: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new CustomExceptions.DocumentUploadException("File cannot be empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new CustomExceptions.DocumentUploadException("File size exceeds maximum limit");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.contains("..")) {
            throw new CustomExceptions.DocumentUploadException("Invalid file name");
        }

        // Check file extension
        String fileExtension = getFileExtension(fileName).toLowerCase();
        if (!isValidFileExtension(fileExtension)) {
            throw new CustomExceptions.DocumentUploadException("File type not supported");
        }
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
    }

    private boolean isValidFileExtension(String extension) {
        String[] allowedExtensions = { "jpg", "jpeg", "png", "pdf", "doc", "docx" };
        for (String allowed : allowedExtensions) {
            if (allowed.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    public byte[] downloadFile(String fileUrl) throws IOException {
        try {
            // Remove leading slash if present
            String cleanUrl = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;

            // Create file path
            Path filePath = Paths.get(cleanUrl);

            // Check if file exists
            if (!Files.exists(filePath)) {
                throw new CustomExceptions.DocumentUploadException("File not found: " + fileUrl);
            }

            // Read file bytes
            byte[] fileBytes = Files.readAllBytes(filePath);
            log.debug("Downloaded file: {} ({} bytes)", fileUrl, fileBytes.length);

            return fileBytes;

        } catch (IOException e) {
            log.error("Failed to download file {}: {}", fileUrl, e.getMessage());
            throw e;
        }
    }

    public void deleteFile(String fileUrl) {
        try {
            // Remove leading slash if present
            String cleanUrl = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;

            // Create file path
            Path filePath = Paths.get(cleanUrl);

            // Delete file if exists
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted file: {}", fileUrl);
            } else {
                log.warn("File not found for deletion: {}", fileUrl);
            }

        } catch (IOException e) {
            log.error("Failed to delete file {}: {}", fileUrl, e.getMessage());
        }
    }

    public Path saveToTempFile(MultipartFile file, String prefix) throws IOException {
        validateFile(file);

        // Get file extension
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(originalFileName);

        // Create temporary file with prefix and extension
        Path tempFile = Files.createTempFile(prefix, "." + fileExtension);

        // Copy file content to temporary file
        Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

        log.debug("Saved temporary file: {} ({} bytes)", tempFile, file.getSize());
        return tempFile;
    }

    public void deleteTempFile(Path tempFile) {
        try {
            if (tempFile != null && Files.exists(tempFile)) {
                Files.delete(tempFile);
                log.debug("Deleted temporary file: {}", tempFile);
            }
        } catch (IOException e) {
            log.warn("Failed to delete temporary file {}: {}", tempFile, e.getMessage());
        }
    }
}