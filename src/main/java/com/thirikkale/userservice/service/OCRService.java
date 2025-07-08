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

            try {
                // Call Python OCR script
                JsonNode result = pythonIntegrationService.executePythonScript("textextract.py", tempFile.toString());

                // Parse the OCR result
                DrivingLicenseInfo licenseInfo = parsePythonOcrResult(result);

                return licenseInfo;

            } finally {
                // Clean up temporary file
                fileStorageService.deleteTempFile(tempFile);
            }

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

            // Log the raw extracted text for debugging
            log.info("Raw OCR extracted text: {}", extractedText);

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
                    .extractedText(extractedText) // Include raw text
                    .confidence(confidence)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse OCR result: {}", e.getMessage());
            throw new RuntimeException("OCR result parsing failed: " + e.getMessage(), e);
        }
    }

    private boolean validateExtractedInfo(DrivingLicenseInfo info) {
        // Check if at least first name and license number are extracted
        return info.getFirstName() != null && !info.getFirstName().trim().isEmpty() &&
                info.getLicenseNumber() != null && !info.getLicenseNumber().trim().isEmpty();
    }

    private String[] extractName(String text) {
        // Enhanced name extraction for Sri Lankan licenses
        String[] result = {"Driver", "User"}; // Default values

        String textUpper = text.toUpperCase();

        // Look for the specific pattern: 1,2. NAME or just after numbers
        Pattern namePattern = Pattern.compile("(?:1,2\\.?\\s*|\\d+\\s+)([A-Z\\s]+(?:[A-Z\\s]+){1,})\\s+(?:SL|BLOOD|ADDRESS|\\d)");
        Matcher matcher = namePattern.matcher(textUpper);

        if (matcher.find()) {
            String fullName = matcher.group(1).trim();
            // Clean up the name
            fullName = fullName.replaceAll("\\s+", " ");

            // Split into first and last name
            String[] parts = fullName.split("\\s+");
            if (parts.length >= 2) {
                result[0] = parts[0]; // First name
                // Combine remaining parts as last name
                StringBuilder lastName = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (lastName.length() > 0) lastName.append(" ");
                    lastName.append(parts[i]);
                }
                result[1] = lastName.toString();
            } else if (parts.length == 1) {
                result[0] = parts[0];
                result[1] = "";
            }
        }

        log.info("Extracted name: {} {}", result[0], result[1]);
        return result;
    }

    private String extractLicenseNumber(String text) {
        // Sri Lankan license format: Letter + 7-8 digits
        Pattern pattern = Pattern.compile("\\b([A-Z]\\d{7,8})\\b");
        Matcher matcher = pattern.matcher(text.toUpperCase());

        if (matcher.find()) {
            String licenseNumber = matcher.group(1);
            log.info("Extracted license number: {}", licenseNumber);
            return licenseNumber;
        }

        return null;
    }

    private String extractExpiryDate(String text) {
        // Look for expiry date patterns
        Pattern[] patterns = {
                Pattern.compile("(?:4a\\.?)(\\d{1,2}\\.\\d{1,2}\\.\\d{4})"),
                Pattern.compile("(?:EXP|EXPIRY|EXPIRES)[:\\s]*(\\d{1,2}\\.\\d{1,2}\\.\\d{4})"),
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String expiryDate = matcher.group(1);
                log.info("Extracted expiry date: {}", expiryDate);
                return expiryDate;
            }
        }

        return null;
    }

    private String extractDateOfBirth(String text) {
        // Look for birth date patterns
        Pattern pattern = Pattern.compile("\\b(\\d{1,2}\\.\\d{1,2}\\.\\d{4})\\b");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String date = matcher.group(1);
            // Check if it's likely a birth date (contains years 1990-2005 for adult drivers)
            if (date.contains("199") || date.contains("200")) {
                log.info("Extracted birth date: {}", date);
                return date;
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
        private String extractedText; // Raw OCR text
        private Double confidence;
    }
}