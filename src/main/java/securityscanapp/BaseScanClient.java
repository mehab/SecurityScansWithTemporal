package securityscanapp;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;

/**
 * Base class for scan orchestrator clients
 * Provides common functionality for initiating security scan workflows
 */
public abstract class BaseScanClient {
    
    protected final WorkflowClient client;
    protected final ScanType supportedScanType;
    
    /**
     * Create a base scan client
     * 
     * @param temporalAddress Temporal service address
     * @param supportedScanType The scan type this client supports
     */
    protected BaseScanClient(String temporalAddress, ScanType supportedScanType) {
        this.supportedScanType = supportedScanType;
        
        // Create Workflow service stub
        WorkflowServiceStubs serviceStub = WorkflowServiceStubs.newServiceStubs(
            io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
                .setTarget(temporalAddress)
                .build()
        );
        
        // Create Workflow client
        this.client = WorkflowClient.newInstance(serviceStub);
    }
    
    /**
     * Submit a scan request
     * Validates that the request is for the supported scan type
     * 
     * @param request Scan request
     * @return Workflow execution handle
     */
    public String submitScan(ScanRequest request) {
        // Validate scan type
        if (request.getToolType() == null) {
            throw new IllegalArgumentException("Tool type must be specified in ScanRequest");
        }
        
        if (request.getToolType() != supportedScanType) {
            throw new IllegalArgumentException(
                "This client only supports " + supportedScanType + 
                " scans. Request has tool type: " + request.getToolType()
            );
        }
        
        // Determine task queue based on scan type
        String taskQueue = Shared.getTaskQueueForScanType(supportedScanType);
        
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
        
        // Log scan initiation
        logScanInitiation(request, workflowId, taskQueue);
        
        // Start workflow execution asynchronously
        io.temporal.api.common.v1.WorkflowExecution execution = 
            WorkflowClient.start(workflow::executeScans, request);
        
        System.out.println("Workflow started successfully");
        System.out.println("Workflow ID: " + workflowId);
        System.out.println("Run ID: " + execution.getRunId());
        System.out.println();
        
        return workflowId;
    }
    
    /**
     * Submit a scan request and wait for completion
     * 
     * @param request Scan request
     * @return Scan summary
     */
    public ScanSummary submitScanAndWait(ScanRequest request) {
        // Validate scan type
        if (request.getToolType() == null) {
            throw new IllegalArgumentException("Tool type must be specified in ScanRequest");
        }
        
        if (request.getToolType() != supportedScanType) {
            throw new IllegalArgumentException(
                "This client only supports " + supportedScanType + 
                " scans. Request has tool type: " + request.getToolType()
            );
        }
        
        // Determine task queue based on scan type
        String taskQueue = Shared.getTaskQueueForScanType(supportedScanType);
        
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
        
        // Log scan initiation
        logScanInitiation(request, workflowId, taskQueue);
        
        // Execute workflow synchronously and wait for result
        ScanSummary summary = workflow.executeScans(request);
        
        System.out.println("Workflow completed");
        System.out.println("All scans successful: " + summary.isAllScansSuccessful());
        System.out.println("Total execution time: " + summary.getTotalExecutionTimeMs() + " ms");
        System.out.println();
        
        return summary;
    }
    
    /**
     * Log scan initiation details
     */
    protected void logScanInitiation(ScanRequest request, String workflowId, String taskQueue) {
        System.out.println("========================================");
        System.out.println("Initiating " + supportedScanType + " scan workflow");
        System.out.println("========================================");
        System.out.println("Application ID: " + request.getAppId());
        System.out.println("Component: " + request.getComponent());
        System.out.println("Build ID: " + request.getBuildId());
        System.out.println("Tool Type: " + request.getToolType());
        System.out.println("Scan ID: " + request.getScanId());
        System.out.println("Workflow ID: " + workflowId);
        System.out.println("Task Queue: " + taskQueue);
        System.out.println("Repository: " + request.getRepositoryUrl());
        System.out.println("Branch: " + request.getBranch());
        if (request.getCommitSha() != null) {
            System.out.println("Commit SHA: " + request.getCommitSha());
        }
        System.out.println("========================================");
    }
    
    /**
     * Get the supported scan type for this client
     */
    public ScanType getSupportedScanType() {
        return supportedScanType;
    }
    
    /**
     * Shutdown the client
     */
    public void shutdown() {
        client.getWorkflowServiceStubs().shutdown();
    }
}
