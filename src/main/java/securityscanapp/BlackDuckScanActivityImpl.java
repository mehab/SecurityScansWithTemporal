package securityscanapp;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Paths;

/**
 * Implementation of BlackDuck Detect scanning activities
 */
public class BlackDuckScanActivityImpl implements BlackDuckScanActivity {
    
    @Override
    public ScanResult scanSignatures(String repoPath, ScanConfig config) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        long startTime = System.currentTimeMillis();
        
        ScanResult result = new ScanResult(ScanType.BLACKDUCK_DETECT, false);
        
        try {
            // Check storage health before accessing repository
            StorageHealthChecker.verifyPathAccessible(repoPath);
            
            context.heartbeat("Starting BlackDuck Detect scan on: " + repoPath);
            
            // Validate BlackDuck configuration
            if (config == null || config.getBlackduckApiToken() == null || 
                config.getBlackduckUrl() == null) {
                throw new RuntimeException("BlackDuck configuration is missing");
            }
            
            // Build detect command
            String[] command = buildDetectCommand(repoPath, config);
            
            context.heartbeat("Executing BlackDuck Detect command");
            
            // Execute detect
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(repoPath));
            processBuilder.redirectErrorStream(true);
            
            // Set environment variables for BlackDuck
            processBuilder.environment().put("BLACKDUCK_API_TOKEN", config.getBlackduckApiToken());
            processBuilder.environment().put("BLACKDUCK_URL", config.getBlackduckUrl());
            
            Process process = processBuilder.start();
            
            // Capture output
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() % 1000 == 0) {
                        context.heartbeat("BlackDuck scanning in progress...");
                    }
                }
            }
            
            int exitCode = process.waitFor();
            
            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);
            result.setOutput(output.toString());
            
            if (exitCode == 0) {
                result.setSuccess(true);
                context.heartbeat("BlackDuck Detect scan completed successfully");
            } else {
                result.setSuccess(false);
                result.setErrorMessage("BlackDuck Detect exited with code: " + exitCode);
                context.heartbeat("BlackDuck Detect scan failed");
            }
            
            return result;
            
        } catch (StorageFailureException e) {
            // Re-throw storage failures as-is (not wrapped)
            // Workflow will handle these specially
            throw e;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);
            result.setSuccess(false);
            result.setErrorMessage("BlackDuck Detect scan failed: " + e.getMessage());
            throw Activity.wrap(new RuntimeException("BlackDuck Detect scan error", e));
        }
    }
    
    private String[] buildDetectCommand(String repoPath, ScanConfig config) {
        java.util.List<String> command = new java.util.ArrayList<>();
        
        // Base command: detect
        command.add("detect");
        
        // Source path
        command.add("--source");
        command.add(repoPath);
        
        // Project name and version
        if (config.getBlackduckProjectName() != null) {
            command.add("--detect.project.name");
            command.add(config.getBlackduckProjectName());
        }
        
        if (config.getBlackduckProjectVersion() != null) {
            command.add("--detect.project.version.name");
            command.add(config.getBlackduckProjectVersion());
        }
        
        // Output directory (outside repo to save space)
        String outputDir = Paths.get(repoPath).getParent().resolve("blackduck-output").toString();
        command.add("--detect.output.path");
        command.add(outputDir);
        
        // Additional options for space efficiency
        command.add("--detect.cleanup");
        command.add("--detect.tools");
        command.add("SIGNATURE_SCAN"); // Only signature scan to save space
        
        return command.toArray(new String[0]);
    }
}

