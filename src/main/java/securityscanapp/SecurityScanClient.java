package securityscanapp;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;

import java.util.Arrays;

/**
 * Client application to initiate security scans
 * This can be used to trigger scans from external systems
 */
public class SecurityScanClient {
    
    public static void main(String[] args) {
        // Get Temporal service connection
        String temporalAddress = System.getenv("TEMPORAL_ADDRESS");
        if (temporalAddress == null || temporalAddress.isEmpty()) {
            temporalAddress = "localhost:7233";
        }
        
        // Create Workflow service stub
        WorkflowServiceStubs serviceStub = WorkflowServiceStubs.newServiceStubs(
            io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
                .setTarget(temporalAddress)
                .build()
        );
        
        // Create Workflow client
        WorkflowClient client = WorkflowClient.newInstance(serviceStub);
        
        // Example: Create a scan request
        ScanRequest request = createExampleScanRequest();
        
        // Determine task queue from config, priority, or auto-detect based on timeout
        String taskQueue = determineTaskQueue(request);
        
        // Configure workflow options with timeout if specified
        WorkflowOptions.Builder optionsBuilder = WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueue)
            .setWorkflowId("security-scan-" + request.getScanId());
        
        // Set workflow execution timeout if configured
        if (request.getScanConfig() != null && request.getScanConfig().getWorkflowTimeoutSeconds() != null) {
            optionsBuilder.setWorkflowExecutionTimeout(
                java.time.Duration.ofSeconds(request.getScanConfig().getWorkflowTimeoutSeconds())
            );
        }
        
        WorkflowOptions options = optionsBuilder.build();
        
        // Create workflow stub
        SecurityScanWorkflow workflow = client.newWorkflowStub(SecurityScanWorkflow.class, options);
        
        System.out.println("Initiating security scan workflow...");
        System.out.println("Scan ID: " + request.getScanId());
        System.out.println("Repository: " + request.getRepositoryUrl());
        System.out.println("Scan Types: " + request.getScanTypes());
        
        // Execute workflow
        try {
            ScanSummary summary = workflow.executeScans(request);
            
            System.out.println("\n=== Scan Summary ===");
            System.out.println("Scan ID: " + summary.getScanId());
            System.out.println("All Scans Successful: " + summary.isAllScansSuccessful());
            System.out.println("Total Execution Time: " + summary.getTotalExecutionTimeMs() + " ms");
            System.out.println("\n=== Individual Scan Results ===");
            
            for (ScanResult result : summary.getScanResults()) {
                System.out.println("\nScan Type: " + result.getScanType());
                System.out.println("Success: " + result.isSuccess());
                System.out.println("Execution Time: " + result.getExecutionTimeMs() + " ms");
                if (result.getErrorMessage() != null) {
                    System.out.println("Error: " + result.getErrorMessage());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Workflow execution failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Shutdown service stub (WorkflowClient doesn't need explicit close)
            serviceStub.shutdown();
        }
    }
    
    private static ScanRequest createExampleScanRequest() {
        ScanRequest request = new ScanRequest(
            "scan-" + System.currentTimeMillis(),
            "https://github.com/example/repo.git",
            "main",
            null
        );
        
        // Add scan types
        request.addScanType(ScanType.GITLEAKS_SECRETS);
        request.addScanType(ScanType.GITLEAKS_FILE_HASH);
        request.addScanType(ScanType.BLACKDUCK_DETECT);
        
        // Configure scan settings
        ScanConfig config = new ScanConfig();
        config.setCleanupAfterEachScan(true);
        
        // For large repositories (4GB+), use space-efficient cloning strategies
        // Option 1: Shallow clone (recommended for large repos)
        config.setCloneStrategy(CloneStrategy.SHALLOW_SINGLE_BRANCH);
        config.setShallowCloneDepth(1); // Only latest commit
        
        // Option 2: Sparse checkout (if you know which paths to scan)
        // config.setUseSparseCheckout(true);
        // config.addSparseCheckoutPath("src/");
        // config.addSparseCheckoutPath("*.java");
        // config.addSparseCheckoutPath("*.js");
        
        // Enable parallel execution for faster scans (uses more resources but same repo space)
        // Set to false for sequential execution (space-efficient, default)
        config.setExecuteScansInParallel(false); // Change to true for parallel execution
        
        // Configure timeouts to prevent long-running scans from blocking the queue
        // Option 1: Set per-scan timeout (default is 30 minutes)
        // config.setScanTimeoutSeconds(900); // 15 minutes for faster scans
        
        // Option 2: Set workflow execution timeout (fails entire workflow if exceeded)
        // config.setWorkflowTimeoutSeconds(3600); // 1 hour total
        
        // Option 3: Use separate task queue for long-running scans
        // config.setTaskQueue(Shared.SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING);
        
        // In production, load credentials from secure storage (Kubernetes Secrets, etc.)
        // config.setGitUsername("user");
        // config.setGitPassword("token");
        // config.setBlackduckApiToken("token");
        // config.setBlackduckUrl("https://blackduck.example.com");
        // config.setBlackduckProjectName("project-name");
        // config.setBlackduckProjectVersion("1.0.0");
        
        request.setScanConfig(config);
        
        return request;
    }
    
    /**
     * Determine the appropriate task queue for a scan request
     * 
     * Priority order:
     * 1. Explicitly set task queue in config
     * 2. Priority-based queue (HIGH -> priority queue)
     * 3. Timeout-based queue (long timeout -> long-running queue)
     * 4. Default queue
     * 
     * @param request Scan request
     * @return Task queue name
     */
    private static String determineTaskQueue(ScanRequest request) {
        ScanConfig config = request.getScanConfig();
        
        // 1. If task queue is explicitly set, use it
        if (config != null && config.getTaskQueue() != null && !config.getTaskQueue().isEmpty()) {
            return config.getTaskQueue();
        }
        
        // 2. If priority is HIGH, use priority queue
        if (config != null && config.getPriority() == ScanPriority.HIGH) {
            return Shared.SECURITY_SCAN_TASK_QUEUE_PRIORITY;
        }
        
        // 3. If scan timeout or workflow timeout indicates long-running scan, use long-running queue
        // Consider it long-running if:
        // - Scan timeout > 30 minutes (1800 seconds)
        // - Workflow timeout > 1 hour (3600 seconds)
        // - Multiple scan types with parallel execution (likely longer)
        boolean isLongRunning = false;
        
        if (config != null) {
            if (config.getScanTimeoutSeconds() != null && config.getScanTimeoutSeconds() > 1800) {
                isLongRunning = true;
            } else if (config.getWorkflowTimeoutSeconds() != null && config.getWorkflowTimeoutSeconds() > 3600) {
                isLongRunning = true;
            } else if (request.getScanTypes() != null && 
                      request.getScanTypes().size() >= 3 && 
                      config.isExecuteScansInParallel()) {
                // Multiple scans in parallel might take longer
                isLongRunning = true;
            }
        }
        
        if (isLongRunning) {
            return Shared.SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING;
        }
        
        // 4. Default queue
        return Shared.SECURITY_SCAN_TASK_QUEUE_DEFAULT;
    }
}

