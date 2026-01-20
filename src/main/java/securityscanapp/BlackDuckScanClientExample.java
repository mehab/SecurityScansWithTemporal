package securityscanapp;

/**
 * Example usage of BlackDuckScanClient
 * This demonstrates how to use the BlackDuck scan orchestrator client
 */
public class BlackDuckScanClientExample {
    
    public static void main(String[] args) {
        // Get Temporal service connection
        String temporalAddress = System.getenv("TEMPORAL_ADDRESS");
        if (temporalAddress == null || temporalAddress.isEmpty()) {
            temporalAddress = "localhost:7233";
        }
        
        // Create BlackDuck scan client
        BlackDuckScanClient client = new BlackDuckScanClient(temporalAddress);
        
        try {
            // Create BlackDuck-specific configuration
            BlackDuckConfig blackDuckConfig = new BlackDuckConfig();
            blackDuckConfig.setAlm("ALM-001"); // ALM identifier
            blackDuckConfig.setSrcFilename("my-repo"); // Source filename
            blackDuckConfig.setSourceSystem("CI_CD_SYSTEM"); // Source system
            blackDuckConfig.setTransId("trans-" + System.currentTimeMillis()); // Transaction ID
            blackDuckConfig.setScanSourceType("RAPID"); // Scan source type (RAPID or FULL)
            blackDuckConfig.setRapidScan(true); // Explicitly set rapid scan
            
            // Hub configuration (can be determined dynamically by activity)
            // blackDuckConfig.setHubUrl("https://hub1.blackduck.com"); // Optional: set explicitly
            // blackDuckConfig.setHubApiToken("token"); // Hub API token
            
            // Project configuration
            blackDuckConfig.setProjectName("my-project");
            blackDuckConfig.setProjectVersion("1.0.0");
            
            // Create a scan request with BlackDuck configuration
            ScanRequest request = client.createScanRequest(
                "app-123",                    // Application ID
                "api-component",              // Component name
                "build-456",                  // Build ID
                "https://github.com/example/repo.git", // SrcURL
                "main",                       // Branch
                "abc123def456",               // Commit SHA
                blackDuckConfig               // BlackDuck-specific config
            );
            
            // Configure scan settings
            ScanConfig config = new ScanConfig();
            config.setCloneStrategy(CloneStrategy.SHALLOW_SINGLE_BRANCH);
            config.setShallowCloneDepth(1);
            
            request.setScanConfig(config);
            
            // Submit scan and wait for completion
            System.out.println("Submitting BlackDuck scan...");
            ScanSummary summary = client.submitScanAndWait(request);
            
            // Process results
            System.out.println("\n=== BlackDuck Scan Summary ===");
            System.out.println("Scan ID: " + summary.getScanId());
            System.out.println("All Scans Successful: " + summary.isAllScansSuccessful());
            System.out.println("Total Execution Time: " + summary.getTotalExecutionTimeMs() + " ms");
            
            for (ScanResult result : summary.getScanResults()) {
                System.out.println("\nScan Type: " + result.getScanType());
                System.out.println("Success: " + result.isSuccess());
                if (result.getErrorMessage() != null) {
                    System.out.println("Error: " + result.getErrorMessage());
                }
            }
            
        } catch (Exception e) {
            System.err.println("BlackDuck scan failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }
}
