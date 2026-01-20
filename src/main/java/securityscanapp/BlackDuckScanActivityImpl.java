package securityscanapp;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implementation of BlackDuck Detect scanning activities
 * 
 * This implementation:
 * 1. Determines the appropriate BlackDuck Hub URL based on scan request
 * 2. Executes the detect shell script with proper parameters
 * 3. Handles rapid scan vs regular scan results
 */
public class BlackDuckScanActivityImpl implements BlackDuckScanActivity {
    
    @Override
    public ScanResult scanSignatures(String repoPath, ScanRequest request) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        long startTime = System.currentTimeMillis();
        
        ScanResult result = new ScanResult(ScanType.BLACKDUCK_DETECT, false);
        
        try {
            // Check storage health before accessing repository
            StorageHealthChecker.verifyPathAccessible(repoPath);
            
            context.heartbeat("Starting BlackDuck Detect scan on: " + repoPath);
            
            // Validate BlackDuck configuration
            BlackDuckConfig blackDuckConfig = request.getBlackDuckConfig();
            if (blackDuckConfig == null) {
                throw new RuntimeException("BlackDuck configuration is missing in ScanRequest");
            }
            
            // Determine hub URL (if not already set)
            String hubUrl = determineHubUrl(blackDuckConfig, request, context);
            blackDuckConfig.setHubUrl(hubUrl);
            
            context.heartbeat("Using BlackDuck Hub: " + hubUrl);
            
            // Determine if this is a rapid scan
            boolean isRapidScan = determineRapidScan(blackDuckConfig, request);
            blackDuckConfig.setRapidScan(isRapidScan);
            
            context.heartbeat("Scan type: " + (isRapidScan ? "Rapid Scan" : "Full Scan"));
            
            // Build detect command using detect shell script
            String[] command = buildDetectCommand(repoPath, request, blackDuckConfig);
            
            context.heartbeat("Executing BlackDuck Detect command");
            
            // Execute detect shell script
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(repoPath));
            processBuilder.redirectErrorStream(true);
            
            // Set environment variables for BlackDuck Detect
            processBuilder.environment().put("BLACKDUCK_API_TOKEN", blackDuckConfig.getHubApiToken());
            processBuilder.environment().put("BLACKDUCK_URL", hubUrl);
            
            // Set Detect-specific environment variables from BlackDuckConfig
            if (blackDuckConfig.getAlm() != null) {
                processBuilder.environment().put("DETECT_ALM", blackDuckConfig.getAlm());
            }
            if (blackDuckConfig.getSourceSystem() != null) {
                processBuilder.environment().put("DETECT_SOURCE_SYSTEM", blackDuckConfig.getSourceSystem());
            }
            if (blackDuckConfig.getTransId() != null) {
                processBuilder.environment().put("DETECT_TRANS_ID", blackDuckConfig.getTransId());
            }
            if (blackDuckConfig.getScanSourceType() != null) {
                processBuilder.environment().put("DETECT_SCAN_SOURCE_TYPE", blackDuckConfig.getScanSourceType());
            }
            
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
                
                // For rapid scans, results may be available immediately
                // For full scans, results may need to be polled
                if (isRapidScan) {
                    context.heartbeat("BlackDuck Rapid Scan completed successfully");
                    result.addMetadata("scanType", "RAPID");
                } else {
                    context.heartbeat("BlackDuck Full Scan completed successfully");
                    result.addMetadata("scanType", "FULL");
                }
                
                // Extract scan results if available
                extractScanResults(repoPath, result, blackDuckConfig);
                
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
    
    /**
     * Determine the appropriate BlackDuck Hub URL for this scan
     * 
     * This method can implement various strategies:
     * - Based on ALM
     * - Based on source system
     * - Based on component/appId
     * - Round-robin or load balancing
     * 
     * @param blackDuckConfig BlackDuck configuration
     * @param request Scan request
     * @param context Activity context for heartbeats
     * @return Hub URL
     */
    private String determineHubUrl(BlackDuckConfig blackDuckConfig, ScanRequest request, 
                                   ActivityExecutionContext context) {
        // If hub URL is already set, use it
        if (blackDuckConfig.getHubUrl() != null && !blackDuckConfig.getHubUrl().isEmpty()) {
            return blackDuckConfig.getHubUrl();
        }
        
        context.heartbeat("Determining BlackDuck Hub URL...");
        
        // Strategy 1: Determine based on ALM
        if (blackDuckConfig.getAlm() != null) {
            // Map ALM to hub URL (implement your mapping logic)
            String hubUrl = mapAlmToHubUrl(blackDuckConfig.getAlm());
            if (hubUrl != null) {
                context.heartbeat("Hub URL determined from ALM: " + hubUrl);
                return hubUrl;
            }
        }
        
        // Strategy 2: Determine based on source system
        if (blackDuckConfig.getSourceSystem() != null) {
            String hubUrl = mapSourceSystemToHubUrl(blackDuckConfig.getSourceSystem());
            if (hubUrl != null) {
                context.heartbeat("Hub URL determined from source system: " + hubUrl);
                return hubUrl;
            }
        }
        
        // Strategy 3: Determine based on component/appId
        if (request.getComponent() != null || request.getAppId() != null) {
            String hubUrl = mapComponentToHubUrl(request.getAppId(), request.getComponent());
            if (hubUrl != null) {
                context.heartbeat("Hub URL determined from component: " + hubUrl);
                return hubUrl;
            }
        }
        
        // Fallback: Use default hub URL from ScanConfig (if available)
        ScanConfig scanConfig = request.getScanConfig();
        if (scanConfig != null && scanConfig.getBlackduckUrl() != null) {
            context.heartbeat("Using default hub URL from config: " + scanConfig.getBlackduckUrl());
            return scanConfig.getBlackduckUrl();
        }
        
        // If no hub URL can be determined, throw exception
        throw new RuntimeException(
            "Cannot determine BlackDuck Hub URL. " +
            "Please provide hub URL in BlackDuckConfig or implement hub determination logic."
        );
    }
    
    /**
     * Map ALM to hub URL
     * Implement your ALM-to-hub mapping logic here
     */
    private String mapAlmToHubUrl(String alm) {
        // TODO: Implement ALM to hub URL mapping
        // Example:
        // if ("ALM1".equals(alm)) return "https://hub1.blackduck.com";
        // if ("ALM2".equals(alm)) return "https://hub2.blackduck.com";
        return null;
    }
    
    /**
     * Map source system to hub URL
     * Implement your source-system-to-hub mapping logic here
     */
    private String mapSourceSystemToHubUrl(String sourceSystem) {
        // TODO: Implement source system to hub URL mapping
        // Example:
        // if ("SYSTEM1".equals(sourceSystem)) return "https://hub1.blackduck.com";
        return null;
    }
    
    /**
     * Map component/appId to hub URL
     * Implement your component-to-hub mapping logic here
     */
    private String mapComponentToHubUrl(String appId, String component) {
        // TODO: Implement component/appId to hub URL mapping
        // This could be based on component name patterns, appId ranges, etc.
        return null;
    }
    
    /**
     * Determine if this is a rapid scan
     * 
     * Rapid scans are typically:
     * - Smaller repositories
     * - Specific scan source types
     * - Configured explicitly
     * 
     * @param blackDuckConfig BlackDuck configuration
     * @param request Scan request
     * @return true if rapid scan, false if full scan
     */
    private boolean determineRapidScan(BlackDuckConfig blackDuckConfig, ScanRequest request) {
        // If explicitly set, use that value
        if (blackDuckConfig.isRapidScan()) {
            return true;
        }
        
        // Strategy 1: Based on scan source type
        if (blackDuckConfig.getScanSourceType() != null) {
            String scanSourceType = blackDuckConfig.getScanSourceType().toUpperCase();
            // Some scan source types indicate rapid scans
            if (scanSourceType.contains("RAPID") || scanSourceType.contains("QUICK")) {
                return true;
            }
        }
        
        // Strategy 2: Based on repository size (if available)
        // Could check repo size and determine if it's small enough for rapid scan
        // This would require checking the repository size first
        
        // Default: full scan
        return false;
    }
    
    /**
     * Build the detect shell script command with all required parameters
     * 
     * Based on BlackDuck Detect documentation, the detect script accepts:
     * - Source path
     * - Hub URL (via BLACKDUCK_URL env var or --blackduck.url)
     * - API token (via BLACKDUCK_API_TOKEN env var or --blackduck.api.token)
     * - Project name and version
     * - Rapid scan flag (--detect.rapid.scan.mode)
     * - Additional parameters from BlackDuckConfig
     */
    private String[] buildDetectCommand(String repoPath, ScanRequest request, 
                                         BlackDuckConfig blackDuckConfig) {
        java.util.List<String> command = new java.util.ArrayList<>();
        
        // Find detect shell script (typically detect.sh or detect8.sh)
        String detectScript = findDetectScript();
        command.add(detectScript);
        
        // Source path (Detect scans the repository directory)
        command.add("--detect.source.path");
        command.add(repoPath);
        
        // Hub URL (can also be set via env var)
        if (blackDuckConfig.getHubUrl() != null) {
            command.add("--blackduck.url");
            command.add(blackDuckConfig.getHubUrl());
        }
        
        // API token (can also be set via env var)
        if (blackDuckConfig.getHubApiToken() != null) {
            command.add("--blackduck.api.token");
            command.add(blackDuckConfig.getHubApiToken());
        }
        
        // Project name and version
        if (blackDuckConfig.getProjectName() != null) {
            command.add("--detect.project.name");
            command.add(blackDuckConfig.getProjectName());
        } else if (request.getScanConfig() != null && 
                   request.getScanConfig().getBlackduckProjectName() != null) {
            command.add("--detect.project.name");
            command.add(request.getScanConfig().getBlackduckProjectName());
        }
        
        if (blackDuckConfig.getProjectVersion() != null) {
            command.add("--detect.project.version.name");
            command.add(blackDuckConfig.getProjectVersion());
        } else if (request.getScanConfig() != null && 
                   request.getScanConfig().getBlackduckProjectVersion() != null) {
            command.add("--detect.project.version.name");
            command.add(request.getScanConfig().getBlackduckProjectVersion());
        }
        
        // Rapid scan mode
        if (blackDuckConfig.isRapidScan()) {
            command.add("--detect.rapid.scan.mode");
            command.add("true");
        }
        
        // Output directory (outside repo to save space)
        String outputDir = Paths.get(repoPath).getParent().resolve("blackduck-output").toString();
        command.add("--detect.output.path");
        command.add(outputDir);
        
        // Additional Detect parameters from BlackDuckConfig
        // These can be passed as environment variables or Detect properties
        // For now, we'll use environment variables (set in scanSignatures method)
        
        // Cleanup option for space efficiency
        command.add("--detect.cleanup");
        
        // Only signature scan to save space
        command.add("--detect.tools");
        command.add("SIGNATURE_SCAN");
        
        // Additional properties that might be useful
        // --detect.code.location.name (optional, for organizing scans)
        if (blackDuckConfig.getTransId() != null) {
            command.add("--detect.code.location.name");
            command.add("scan-" + blackDuckConfig.getTransId());
        }
        
        return command.toArray(new String[0]);
    }
    
    /**
     * Find the detect shell script
     * Looks for detect.sh, detect8.sh, or detect script in common locations
     */
    private String findDetectScript() {
        // Common locations for detect script
        String[] possiblePaths = {
            "/usr/local/bin/detect.sh",
            "/usr/local/bin/detect8.sh",
            "/opt/blackduck/detect.sh",
            "/opt/blackduck/detect8.sh",
            "detect.sh",
            "detect8.sh",
            "./detect.sh",
            "./detect8.sh"
        };
        
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists() && file.canExecute()) {
                return path;
            }
        }
        
        // If not found, try to use detect command directly (if in PATH)
        // BlackDuck Detect is often installed as a command
        return "detect";
    }
    
    /**
     * Extract scan results from Detect output
     * 
     * For rapid scans, results may be available immediately
     * For full scans, results may need to be polled from the Hub
     */
    private void extractScanResults(String repoPath, ScanResult result, 
                                     BlackDuckConfig blackDuckConfig) {
        try {
            // Look for Detect output files
            Path outputDir = Paths.get(repoPath).getParent().resolve("blackduck-output");
            
            if (Files.exists(outputDir)) {
                // Detect typically creates:
                // - bdio.json (scan results)
                // - scan.log (scan log)
                // - rbdump.json (rapid scan results, if rapid scan)
                
                Path bdioFile = outputDir.resolve("bdio.json");
                if (Files.exists(bdioFile)) {
                    result.addMetadata("bdioFile", bdioFile.toString());
                }
                
                Path scanLog = outputDir.resolve("scan.log");
                if (Files.exists(scanLog)) {
                    result.addMetadata("scanLog", scanLog.toString());
                }
                
                // For rapid scans, check for rapid scan results
                if (blackDuckConfig.isRapidScan()) {
                    Path rbdumpFile = outputDir.resolve("rbdump.json");
                    if (Files.exists(rbdumpFile)) {
                        result.addMetadata("rapidScanResults", rbdumpFile.toString());
                        result.addMetadata("resultsAvailable", "true");
                    }
                } else {
                    // For full scans, results may need to be polled from Hub
                    result.addMetadata("resultsAvailable", "false");
                    result.addMetadata("pollRequired", "true");
                    result.addMetadata("hubUrl", blackDuckConfig.getHubUrl());
                }
            }
        } catch (Exception e) {
            // Log but don't fail the scan
            System.err.println("Error extracting scan results: " + e.getMessage());
        }
    }
}
