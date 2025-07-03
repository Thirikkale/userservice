package com.thirikkale.userservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.thirikkale.userservice.dto.response.OcrExtractionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class OCRService {

    private final PythonIntegrationService pythonIntegrationService;
    private final FileStorageService fileStorageService;

    public DrivingLicenseInfo extractDrivingLicenseInfo(MultipartFile licenseFile) {
        try {
            log.info("Extracting information from driving license using EasyOCR");

            // Save the file to a temporary location for Python processing
            Path tempFile = fileStorageService.saveToTempFile(licenseFile, "license_ocr");

            // Call Python OCR script
            JsonNode result = pythonIntegrationService.executePythonScript("textextract.py", tempFile.toString());

            // Parse the OCR result
            DrivingLicenseInfo licenseInfo = parsePythonOcrResult(result);

            // Clean up temporary file
            fileStorageService.deleteTempFile(tempFile);

            return licenseInfo;

        } catch (Exception e) {
            log.error("Failed to process driving license image: {}", e.getMessage());
            throw new RuntimeException("OCR processing failed: " + e.getMessage(), e);
        }
    }

    public OcrExtractionResponse extractLicenseInformation(UUID driverId, MultipartFile licenseFile) {
        try {
            DrivingLicenseInfo licenseInfo = extractDrivingLicenseInfo(licenseFile);

            // Validate extracted information
            boolean isValid = validateExtractedInfo(licenseInfo);

            if (isValid) {
                return OcrExtractionResponse.success(
                        driverId,
                        licenseInfo.getFirstName(),
                        licenseInfo.getLastName(),
                        licenseInfo.getLicenseNumber(),
                        licenseInfo.getExpiryDate(),
                        licenseInfo.getDateOfBirth(),
                        licenseInfo.getExtractedText(),
                        licenseInfo.getConfidence());
            } else {
                return OcrExtractionResponse.failure(
                        driverId,
                        "Could not extract valid information from license",
                        licenseInfo.getExtractedText());
            }
        } catch (Exception e) {
            log.error("OCR extraction failed for driver {}: {}", driverId, e.getMessage());
            return OcrExtractionResponse.failure(
                    driverId,
                    "OCR processing failed: " + e.getMessage(),
                    "");
        }
    }

    private DrivingLicenseInfo parsePythonOcrResult(JsonNode result) {
        try {
            String extractedText = result.has("extracted_text") ? result.get("extracted_text").asText() : "";
            double confidence = result.has("confidence") ? result.get("confidence").asDouble() : 0.0;

            // Extract structured information from the OCR text
            String[] nameParts = extractName(extractedText);
            String firstName = nameParts[0];
            String lastName = nameParts[1];

            String licenseNumber = extractLicenseNumber(extractedText);
            String expiryDate = extractExpiryDate(extractedText);
            String dateOfBirth = extractDateOfBirth(extractedText);

            return DrivingLicenseInfo.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .licenseNumber(licenseNumber)
                    .expiryDate(expiryDate)
                    .dateOfBirth(dateOfBirth)
                    .extractedText(extractedText)
                    .confidence(confidence)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Python OCR result: {}", e.getMessage());
            throw new RuntimeException("Failed to parse OCR result", e);
        }
    }

    private boolean validateExtractedInfo(DrivingLicenseInfo info) {
        // Basic validation - at least name and license number should be present
        return info.getFirstName() != null && !info.getFirstName().trim().isEmpty() &&
                info.getLicenseNumber() != null && !info.getLicenseNumber().trim().isEmpty();
    }

    private String[] extractName(String text) {
        // Look for "Name:" pattern
        Pattern namePattern = Pattern.compile("Name:\\s*([A-Z\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = namePattern.matcher(text);

        if (matcher.find()) {
            String fullName = matcher.group(1).trim();
            String[] parts = fullName.split("\\s+");

            if (parts.length >= 2) {
                return new String[] { parts[0], parts[parts.length - 1] };
            } else if (parts.length == 1) {
                return new String[] { parts[0], "" };
            }
        }

        // Default fallback
        return new String[] { "Driver", "User" };
    }

    private String extractLicenseNumber(String text) {
        // Look for license number patterns
        Pattern licensePattern = Pattern.compile("License No[.:]*\\s*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = licensePattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    private String extractExpiryDate(String text) {
        // Look for expiry date patterns (DD/MM/YYYY or DD-MM-YYYY)
        Pattern expiryPattern = Pattern.compile("Valid Till[.:]*\\s*(\\d{2}[/-]\\d{2}[/-]\\d{4})",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = expiryPattern.matcher(text);

        if (matcher.find()) {
            String date = matcher.group(1);
            // Convert to standard format (YYYY-MM-DD)
            if (date.contains("/")) {
                String[] parts = date.split("/");
                return parts[2] + "-" + parts[1] + "-" + parts[0];
            } else if (date.contains("-")) {
                String[] parts = date.split("-");
                return parts[2] + "-" + parts[1] + "-" + parts[0];
            }
        }

        return null;
    }

    private String extractDateOfBirth(String text) {
        // Look for DOB patterns
        Pattern dobPattern = Pattern.compile("DOB[.:]*\\s*(\\d{2}[/-]\\d{2}[/-]\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = dobPattern.matcher(text);

        if (matcher.find()) {
            String date = matcher.group(1);
            // Convert to standard format (YYYY-MM-DD)
            if (date.contains("/")) {
                String[] parts = date.split("/");
                return parts[2] + "-" + parts[1] + "-" + parts[0];
            } else if (date.contains("-")) {
                String[] parts = date.split("-");
                return parts[2] + "-" + parts[1] + "-" + parts[0];
            }
        }

        return null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrivingLicenseInfo {
        private String firstName;
        private String lastName;
        private String licenseNumber;
        private String expiryDate;
        private String dateOfBirth;
        private String extractedText;
        private Double confidence;
    }
}