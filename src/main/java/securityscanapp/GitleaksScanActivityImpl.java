package securityscanapp;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Paths;

/**
 * Implementation of Gitleaks scanning activities
 */
public class GitleaksScanActivityImpl implements GitleaksScanActivity {
    
    @Override
    public ScanResult scanSecrets(String repoPath, ScanConfig config) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        long startTime = System.currentTimeMillis();
        
        ScanResult result = new ScanResult(ScanType.GITLEAKS_SECRETS, false);
        
        try {
            // Check storage health before accessing repository
            StorageHealthChecker.verifyPathAccessible(repoPath);
            
            context.heartbeat("Starting Gitleaks secrets scan on: " + repoPath);
            
            // Build gitleaks command
            String[] command = buildGitleaksCommand(repoPath, config, "detect");
            
            context.heartbeat("Executing gitleaks command");
            
            // Execute gitleaks
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(repoPath));
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // Capture output
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    // Send heartbeat periodically
                    if (output.length() % 1000 == 0) {
                        context.heartbeat("Scanning in progress...");
                    }
                }
            }
            
            int exitCode = process.waitFor();
            
            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);
            result.setOutput(output.toString());
            
            // Gitleaks returns non-zero exit code if secrets are found
            // This is expected behavior, so we consider it successful
            if (exitCode == 0 || exitCode == 1) {
                result.setSuccess(true);
                result.addMetadata("exitCode", String.valueOf(exitCode));
                result.addMetadata("secretsFound", exitCode == 1 ? "true" : "false");
                context.heartbeat("Gitleaks secrets scan completed");
            } else {
                result.setSuccess(false);
                result.setErrorMessage("Gitleaks exited with code: " + exitCode);
                context.heartbeat("Gitleaks secrets scan failed");
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
            result.setErrorMessage("Gitleaks secrets scan failed: " + e.getMessage());
            throw Activity.wrap(new RuntimeException("Gitleaks secrets scan error", e));
        }
    }
    
    @Override
    public ScanResult scanFileHash(String repoPath, ScanConfig config) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        long startTime = System.currentTimeMillis();
        
        ScanResult result = new ScanResult(ScanType.GITLEAKS_FILE_HASH, false);
        
        try {
            // Check storage health before accessing repository
            StorageHealthChecker.verifyPathAccessible(repoPath);
            
            context.heartbeat("Starting Gitleaks file hash scan on: " + repoPath);
            
            // Build gitleaks command for file hash scanning
            String[] command = buildGitleaksCommand(repoPath, config, "detect", "--baseline-path");
            
            context.heartbeat("Executing gitleaks file hash command");
            
            // Execute gitleaks
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(repoPath));
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // Capture output
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() % 1000 == 0) {
                        context.heartbeat("File hash scanning in progress...");
                    }
                }
            }
            
            int exitCode = process.waitFor();
            
            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);
            result.setOutput(output.toString());
            
            if (exitCode == 0 || exitCode == 1) {
                result.setSuccess(true);
                result.addMetadata("exitCode", String.valueOf(exitCode));
                context.heartbeat("Gitleaks file hash scan completed");
            } else {
                result.setSuccess(false);
                result.setErrorMessage("Gitleaks exited with code: " + exitCode);
                context.heartbeat("Gitleaks file hash scan failed");
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
            result.setErrorMessage("Gitleaks file hash scan failed: " + e.getMessage());
            throw Activity.wrap(new RuntimeException("Gitleaks file hash scan error", e));
        }
    }
    
    private String[] buildGitleaksCommand(String repoPath, ScanConfig config, String... additionalArgs) {
        // Base command: gitleaks detect
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("gitleaks");
        command.add("detect");
        command.add("--source");
        command.add(repoPath);
        command.add("--no-banner");
        command.add("--verbose");
        
        // Add config file if provided
        if (config != null && config.getGitleaksConfigPath() != null) {
            command.add("--config-path");
            command.add(config.getGitleaksConfigPath());
        }
        
        // Add report path
        String reportPath = Paths.get(repoPath).getParent().resolve("gitleaks-report.json").toString();
        command.add("--report-path");
        command.add(reportPath);
        
        // Add any additional arguments
        for (String arg : additionalArgs) {
            command.add(arg);
        }
        
        return command.toArray(new String[0]);
    }
}

