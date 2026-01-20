package securityscanapp;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;


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
        
        // Determine task queue based on scan type (tool type)
        // Each scan type has its own dedicated queue for dedicated workers
        String taskQueue = determineTaskQueue(request);
        
        // Generate workflow ID: appId-component-buildId-toolType
        String workflowId = request.generateWorkflowId();
        
        // Configure workflow options with timeout if specified
        WorkflowOptions.Builder optionsBuilder = WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueue)
            .setWorkflowId(workflowId);
        
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
        System.out.println("Application ID: " + request.getAppId());
        System.out.println("Component: " + request.getComponent());
        System.out.println("Build ID: " + request.getBuildId());
        System.out.println("Tool Type: " + request.getToolType());
        System.out.println("Scan ID: " + request.getScanId());
        System.out.println("Workflow ID: " + workflowId);
        System.out.println("Task Queue: " + taskQueue);
        System.out.println("Repository: " + request.getRepositoryUrl());
        
        // Execute workflow
        try {
            ScanSummary summary = workflow.executeScans(request);
            
            System.out.println("\n=== Scan Summary ===");
            System.out.println("Scan ID: " + summary.getScanId());
            System.out.println("Scan Successful: " + summary.isAllScansSuccessful());
            System.out.println("Total Execution Time: " + summary.getTotalExecutionTimeMs() + " ms");
            System.out.println("\n=== Scan Result ===");
            
            // Currently only one scan result (BlackDuck)
            if (!summary.getScanResults().isEmpty()) {
                ScanResult result = summary.getScanResults().get(0);
                System.out.println("Scan Type: " + result.getScanType());
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
        // Example using structure: appId, component, buildId, toolType
        ScanRequest request = new ScanRequest(
            "app-123",                    // Application ID
            "component-api",              // Component name
            "build-456",                  // Build ID
                        ScanType.BLACKDUCK_DETECT,    // Tool type (single scan type per request)
            "https://github.com/example/repo.git",
            "main",
            "abc123def456"                // Commit SHA
        );
        
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
        
        // Note: Parallel execution is not applicable since only one scan type (BlackDuck) is supported
        
        // Configure timeouts
        // Option 1: Set per-scan timeout (default is 30 minutes)
        // config.setScanTimeoutSeconds(900); // 15 minutes for faster scans
        
        // Option 2: Set workflow execution timeout (fails entire workflow if exceeded)
        // config.setWorkflowTimeoutSeconds(3600); // 1 hour total
        
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
     * 2. Scan type-based queue (each tool type has its own queue)
     * 3. Default queue (fallback)
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
        
        // 2. Route based on scan type (tool type) - each tool type has its own queue
        // This ensures one worker per scan type
        ScanType toolType = request.getToolType();
        if (toolType != null) {
            return Shared.getTaskQueueForScanType(toolType);
        }
        
        // 3. Default queue (fallback)
        return Shared.SECURITY_SCAN_TASK_QUEUE_DEFAULT;
    }
}

