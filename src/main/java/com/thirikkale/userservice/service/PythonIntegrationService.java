package com.thirikkale.userservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PythonIntegrationService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${python.fastapi.url:http://127.0.0.1:8001}")
    private String fastApiUrl;

    @Value("${python.fastapi.timeout:200}")
    private int timeoutSeconds;

    /**
     * Execute Python script via FastAPI service
     */
    public JsonNode executePythonScript(String scriptName, String... args) {
        try {
            log.info("Executing {} via FastAPI with args: {}", scriptName, String.join(", ", args));

            switch (scriptName) {
                case "face_verification.py":
                    return callFaceVerificationAPI(args[0], args[1]);

                case "textextract.py":
                    return callOCRExtractionAPI(args[0]);

                case "gender_detection.py":
                    return callGenderDetectionAPI(args[0]);

                default:
                    throw new RuntimeException("Unknown script: " + scriptName);
            }

        } catch (Exception e) {
            log.error("Python script execution failed for {}: {}", scriptName, e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Call FastAPI Face Verification endpoint
     */
    private JsonNode callFaceVerificationAPI(String image1Path, String image2Path) {
        try {
            log.info("Calling FastAPI face verification: {} vs {}", image1Path, image2Path);

            String url = fastApiUrl + "/ai/face-verification";

            // Create request body
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("image1_path", image1Path);
            requestBody.put("image2_path", image2Path);

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Call FastAPI service
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                log.info("FastAPI face verification response: {}", jsonResponse);

                // Convert FastAPI response to expected format
                return convertFaceVerificationResponse(jsonResponse);
            } else {
                throw new RuntimeException("FastAPI returned status: " + response.getStatusCode());
            }

        } catch (ResourceAccessException e) {
            log.error("FastAPI service connection failed: {}", e.getMessage());
            return createErrorResponse("FastAPI service not available: " + e.getMessage());
        } catch (Exception e) {
            log.error("Face verification API call failed: {}", e.getMessage());
            return createErrorResponse("Face verification failed: " + e.getMessage());
        }
    }

    /**
     * Call FastAPI OCR Extraction endpoint
     */
    private JsonNode callOCRExtractionAPI(String imagePath) {
        try {
            log.info("Calling FastAPI OCR extraction: {}", imagePath);

            String url = fastApiUrl + "/ai/ocr-extraction";

            // Create request body
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("image_path", imagePath);

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Call FastAPI service
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                log.info("FastAPI OCR response received, text length: {}",
                        jsonResponse.has("raw_text") ? jsonResponse.get("raw_text").asText().length() : 0);

                // Convert FastAPI response to expected format
                return convertOCRResponse(jsonResponse);
            } else {
                throw new RuntimeException("FastAPI returned status: " + response.getStatusCode());
            }

        } catch (ResourceAccessException e) {
            log.error("FastAPI service connection failed: {}", e.getMessage());
            return createErrorResponse("FastAPI service not available: " + e.getMessage());
        } catch (Exception e) {
            log.error("OCR extraction API call failed: {}", e.getMessage());
            return createErrorResponse("OCR extraction failed: " + e.getMessage());
        }
    }

    /**
     * Call FastAPI Gender Detection endpoint
     */
    private JsonNode callGenderDetectionAPI(String imagePath) {
        try {
            log.info("Calling FastAPI gender detection: {}", imagePath);

            String url = fastApiUrl + "/ai/gender-detection";

            // Create request body
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("image_path", imagePath);

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Call FastAPI service
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                log.info("FastAPI gender detection response: {}", jsonResponse);
                return jsonResponse;
            } else {
                throw new RuntimeException("FastAPI returned status: " + response.getStatusCode());
            }

        } catch (ResourceAccessException e) {
            log.error("FastAPI service connection failed: {}", e.getMessage());
            return createErrorResponse("FastAPI service not available: " + e.getMessage());
        } catch (Exception e) {
            log.error("Gender detection API call failed: {}", e.getMessage());
            return createErrorResponse("Gender detection failed: " + e.getMessage());
        }
    }

    /**
     * Convert FastAPI face verification response to expected format
     */
    private JsonNode convertFaceVerificationResponse(JsonNode fastApiResponse) {
        try {
            Map<String, Object> convertedResponse = new HashMap<>();

            boolean success = !fastApiResponse.has("error");
            boolean verified = fastApiResponse.has("verified") ? fastApiResponse.get("verified").asBoolean() : false;
            double similarityScore = fastApiResponse.has("similarity_score") ?
                    fastApiResponse.get("similarity_score").asDouble() : 0.0;
            double confidence = fastApiResponse.has("confidence") ?
                    fastApiResponse.get("confidence").asDouble() : similarityScore;
            double threshold = fastApiResponse.has("threshold") ?
                    fastApiResponse.get("threshold").asDouble() : 0.6;

            convertedResponse.put("success", success);
            convertedResponse.put("verified", verified);
            convertedResponse.put("similarity_score", similarityScore);
            convertedResponse.put("confidence", confidence);
            convertedResponse.put("threshold", threshold);
            convertedResponse.put("model", fastApiResponse.has("model") ?
                    fastApiResponse.get("model").asText() : "FastAPI");

            if (fastApiResponse.has("error")) {
                convertedResponse.put("error", fastApiResponse.get("error").asText());
            }

            log.info("Converted face verification response: verified={}, similarity={}", verified, similarityScore);

            return objectMapper.valueToTree(convertedResponse);

        } catch (Exception e) {
            log.error("Failed to convert face verification response: {}", e.getMessage());
            return createErrorResponse("Response conversion failed: " + e.getMessage());
        }
    }

    /**
     * Convert FastAPI OCR response to expected format
     */
    private JsonNode convertOCRResponse(JsonNode fastApiResponse) {
        try {
            Map<String, Object> convertedResponse = new HashMap<>();

            boolean success = !fastApiResponse.has("error");
            String extractedText = fastApiResponse.has("raw_text") ?
                    fastApiResponse.get("raw_text").asText() : "";
            String cleanedText = fastApiResponse.has("cleaned_text") ?
                    fastApiResponse.get("cleaned_text").asText() : extractedText;

            convertedResponse.put("success", success);
            convertedResponse.put("extracted_text", extractedText);
            convertedResponse.put("cleaned_text", cleanedText);
            convertedResponse.put("confidence", fastApiResponse.has("confidence_scores") ? 0.95 : 0.0);

            // Add license information if available
            if (fastApiResponse.has("license_info")) {
                convertedResponse.put("license_info", fastApiResponse.get("license_info"));
            }

            if (fastApiResponse.has("error")) {
                convertedResponse.put("error", fastApiResponse.get("error").asText());
            }

            log.info("Converted OCR response: text_length={}, success={}", extractedText.length(), success);

            return objectMapper.valueToTree(convertedResponse);

        } catch (Exception e) {
            log.error("Failed to convert OCR response: {}", e.getMessage());
            return createErrorResponse("Response conversion failed: " + e.getMessage());
        }
    }

    /**
     * Create error response in expected format
     */
    private JsonNode createErrorResponse(String errorMessage) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", errorMessage);
        errorResponse.put("verified", false);
        errorResponse.put("similarity_score", 0.0);
        errorResponse.put("confidence", 0.0);
        errorResponse.put("extracted_text", "");

        return objectMapper.valueToTree(errorResponse);
    }

    /**
     * Check if FastAPI service is running
     */
    public boolean isServiceAvailable() {
        try {
            String url = fastApiUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            boolean available = response.getStatusCode() == HttpStatus.OK;
            log.info("FastAPI service availability check: {}", available);
            return available;

        } catch (Exception e) {
            log.warn("FastAPI service not available: {}", e.getMessage());
            return false;
        }
    }
}