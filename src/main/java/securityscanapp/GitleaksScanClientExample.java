package securityscanapp;

/**
 * Example usage of GitleaksScanClient
 * This demonstrates how to use the Gitleaks scan orchestrator client
 */
public class GitleaksScanClientExample {
    
    public static void main(String[] args) {
        // Get Temporal service connection
        String temporalAddress = System.getenv("TEMPORAL_ADDRESS");
        if (temporalAddress == null || temporalAddress.isEmpty()) {
            temporalAddress = "localhost:7233";
        }
        
        // Create Gitleaks scan client for secrets scanning
        GitleaksScanClient client = new GitleaksScanClient(temporalAddress, ScanType.GITLEAKS_SECRETS);
        
        try {
            // Create a scan request using helper method
            ScanRequest request = client.createScanRequest(
                "app-123",                    // Application ID
                "api-component",              // Component name
                "build-456",                  // Build ID
                "https://github.com/example/repo.git",
                "main",                       // Branch
                "abc123def456"                // Commit SHA
            );
            
            // Configure scan settings
            ScanConfig config = new ScanConfig();
            config.setCloneStrategy(CloneStrategy.SHALLOW_SINGLE_BRANCH);
            config.setShallowCloneDepth(1);
            
            request.setScanConfig(config);
            
            // Submit scan asynchronously (non-blocking)
            System.out.println("Submitting Gitleaks scan...");
            String workflowId = client.submitScan(request);
            
            System.out.println("Scan submitted successfully. Workflow ID: " + workflowId);
            System.out.println("Monitor workflow status in Temporal UI or use WorkflowClient to query status");
            
            // Alternatively, wait for completion:
            // ScanSummary summary = client.submitScanAndWait(request);
            
        } catch (Exception e) {
            System.err.println("Gitleaks scan failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }
}
