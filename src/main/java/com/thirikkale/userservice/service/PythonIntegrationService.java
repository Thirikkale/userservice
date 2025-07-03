package com.thirikkale.userservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PythonIntegrationService {

    @Value("${python.interpreter:python}")
    private String pythonInterpreter;

    @Value("${python.scripts.path:d:/Backend/userservice/python_scripts}")
    private String scriptsPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode executePythonScript(String scriptName, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add(pythonInterpreter);
            command.add(Paths.get(scriptsPath, scriptName).toString());

            // Add arguments
            for (String arg : args) {
                command.add(arg);
            }

            log.info("Executing Python command: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(Paths.get(scriptsPath).toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Python script execution timeout");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Python script failed with exit code: {}. Output: {}", exitCode, output.toString());
                throw new RuntimeException("Python script execution failed: " + output.toString());
            }

            String result = output.toString().trim();
            log.info("Python script output: {}", result);

            return objectMapper.readTree(result);

        } catch (IOException | InterruptedException e) {
            log.error("Failed to execute Python script {}: {}", scriptName, e.getMessage());
            throw new RuntimeException("Python script execution failed", e);
        }
    }

    public boolean isPythonEnvironmentReady() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(pythonInterpreter, "--version");
            Process process = processBuilder.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.error("Python environment check failed: {}", e.getMessage());
            return false;
        }
    }

    public void ensureScriptsDirectoryExists() {
        try {
            Path scriptsDir = Paths.get(scriptsPath);
            if (!Files.exists(scriptsDir)) {
                Files.createDirectories(scriptsDir);
                log.info("Created Python scripts directory: {}", scriptsPath);
            }
        } catch (IOException e) {
            log.error("Failed to create scripts directory: {}", e.getMessage());
            throw new RuntimeException("Failed to create scripts directory", e);
        }
    }
}
