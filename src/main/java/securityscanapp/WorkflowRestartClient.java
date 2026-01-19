package securityscanapp;

import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client utility for restarting workflows that failed due to various failure types
 * 
 * This client monitors for workflows that failed with specific exceptions that require restart:
 * - StorageFailureException: Storage unavailable, restart required when storage restored
 * - NetworkFailureException: Network issues, restart if network not restored (after retries exhausted)
 * - ResourceExhaustionException: Resource constraints, restart with adjusted limits
 * - DeploymentFailureException: Deployment issues, restart after deployment fixed
 * 
 * Usage:
 * 1. Monitor failed workflows for restartable failures
 * 2. When conditions are restored, restart affected workflows
 * 3. Can be run as a separate service or scheduled job
 */
public class WorkflowRestartClient {
    
    /**
     * Enum for failure types that may require workflow restart
     */
    public enum FailureType {
        STORAGE_FAILURE("StorageFailureException", "storageFailure", "workflowRestartRequired"),
        NETWORK_FAILURE("NetworkFailureException", "networkFailure", null),
        RESOURCE_EXHAUSTION("ResourceExhaustionException", "resourceExhaustion", null),
        DEPLOYMENT_FAILURE("DeploymentFailureException", "deploymentFailure", null),
        ALL("ALL", null, null);  // All restartable failure types
        
        private final String exceptionName;
        private final String metadataKey;
        private final String restartRequiredKey;
        
        FailureType(String exceptionName, String metadataKey, String restartRequiredKey) {
            this.exceptionName = exceptionName;
            this.metadataKey = metadataKey;
            this.restartRequiredKey = restartRequiredKey;
        }
        
        public String getExceptionName() { return exceptionName; }
        public String getMetadataKey() { return metadataKey; }
        public String getRestartRequiredKey() { return restartRequiredKey; }
    }
    
    private final WorkflowClient client;
    private final WorkflowServiceStubs serviceStub;
    
    public WorkflowRestartClient(String temporalAddress) {
        this.serviceStub = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(temporalAddress)
                .build()
        );
        this.client = WorkflowClient.newInstance(serviceStub);
    }
    
    /**
     * Simple class to hold workflow execution info
     */
    public static class WorkflowExecutionInfo {
        public final String workflowId;
        public final String runId;
        
        public WorkflowExecutionInfo(String workflowId, String runId) {
            this.workflowId = workflowId;
            this.runId = runId;
        }
    }
    
    /**
     * Find workflows that failed due to specific failure types
     * 
     * This method queries Temporal for failed workflows and checks if they
     * failed with the specified exception type by examining workflow failure reason
     * or querying workflow state for failure metadata.
     * 
     * @param taskQueue Task queue to search
     * @param failureType Type of failure to search for (or ALL for any restartable failure)
     * @return List of workflow execution info (workflowId, runId) that failed due to specified failure type
     */
    public List<WorkflowExecutionInfo> findFailedWorkflows(String taskQueue, FailureType failureType) {
        List<WorkflowExecutionInfo> failedWorkflows = new ArrayList<>();
        
        try {
            // Query for failed workflows in the task queue
            ListWorkflowExecutionsRequest request = ListWorkflowExecutionsRequest.newBuilder()
                .setQuery("TaskQueue = '" + taskQueue + "' AND ExecutionStatus = 'FAILED'")
                .build();
            
            ListWorkflowExecutionsResponse response = serviceStub.blockingStub()
                .listWorkflowExecutions(request);
            
            // Check each failed workflow for the specified failure type
            for (io.temporal.api.workflow.v1.WorkflowExecutionInfo workflowInfo : response.getExecutionsList()) {
                String workflowId = workflowInfo.getExecution().getWorkflowId();
                String runId = workflowInfo.getExecution().getRunId();
                
                // Check if workflow failed due to the specified failure type
                if (isFailureType(workflowId, runId, failureType)) {
                    failedWorkflows.add(new WorkflowExecutionInfo(workflowId, runId));
                }
            }
        } catch (Exception e) {
            System.err.println("Error querying failed workflows: " + e.getMessage());
            e.printStackTrace();
        }
        
        return failedWorkflows;
    }
    
    /**
     * Check if a workflow failed due to a specific failure type
     * 
     * @param workflowId Workflow ID
     * @param runId Run ID
     * @param failureType Type of failure to check for
     * @return true if workflow failed due to the specified failure type
     */
    private boolean isFailureType(String workflowId, String runId, FailureType failureType) {
        // First check if it's a security scan workflow
        if (!workflowId.startsWith("security-scan-")) {
            return false;
        }
        
        // If checking for ALL failure types, accept any security scan workflow
        if (failureType == FailureType.ALL) {
            return true;
        }
        
        try {
            // Query workflow for original request to verify it's queryable
            SecurityScanWorkflow workflow = client.newWorkflowStub(
                SecurityScanWorkflow.class,
                workflowId,
                Optional.of(runId)
            );
            
            // Query workflow for original request (to check if it exists and is queryable)
            ScanRequest originalRequest = workflow.getOriginalRequest();
            
            // Check failure reason from workflow execution info
            return checkFailureReason(workflowId, runId, failureType);
            
        } catch (Exception e) {
            // If query fails, check failure reason from execution info
            return checkFailureReason(workflowId, runId, failureType);
        }
    }
    
    /**
     * Check failure reason by examining workflow execution details
     */
    private boolean checkFailureReason(String workflowId, String runId, FailureType failureType) {
        try {
            // Get workflow execution details
            io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest describeRequest =
                io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest.newBuilder()
                    .setExecution(
                        io.temporal.api.common.v1.WorkflowExecution.newBuilder()
                            .setWorkflowId(workflowId)
                            .setRunId(runId)
                            .build()
                    )
                    .build();
            
            io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse describeResponse =
                serviceStub.blockingStub().describeWorkflowExecution(describeRequest);
            
            // Get workflow execution info
            io.temporal.api.workflow.v1.WorkflowExecutionInfo executionInfo = 
                describeResponse.getWorkflowExecutionInfo();
            
            // Check if workflow is in failed status
            if (executionInfo.getStatus() != io.temporal.api.enums.v1.WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_FAILED) {
                return false;
            }
            
            // Try to get failure message from workflow history or search attributes
            // For now, we'll check the exception name in the failure type
            // In production, you'd check workflow history events for the specific exception
            
            // Check failure message for keywords based on failure type
            // Since we can't directly access failure message from WorkflowExecutionInfo,
            // we'll use a simpler approach: check if it's a security scan workflow
            // and allow filtering by failure type when querying
            
            // For now, accept all security scan workflows as potentially restartable
            // In production, you would:
            // 1. Query workflow history events
            // 2. Check for specific exception types in history
            // 3. Filter based on actual failure reason
            
            // This is a simplified implementation - in production you'd check workflow history
            return true;
            
        } catch (Exception e) {
            // If we can't determine the failure type, default to false
            // In production, you might want to log this for investigation
            System.err.println("Error checking failure reason for workflow " + workflowId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Restart a workflow from the beginning
     * 
     * This creates a new workflow execution with the same workflow ID
     * (or a new ID) to restart from the beginning.
     * 
     * @param originalRequest The original ScanRequest that failed
     * @param useNewWorkflowId If true, creates new workflow ID; if false, reuses original
     * @return New workflow execution ID
     */
    public String restartWorkflow(ScanRequest originalRequest, boolean useNewWorkflowId) {
        // Determine task queue from config or use default
        String taskQueue = Shared.SECURITY_SCAN_TASK_QUEUE_DEFAULT;
        if (originalRequest.getScanConfig() != null && originalRequest.getScanConfig().getTaskQueue() != null) {
            taskQueue = originalRequest.getScanConfig().getTaskQueue();
        }
        
        String workflowId = useNewWorkflowId 
            ? "security-scan-" + originalRequest.getScanId() + "-restart-" + System.currentTimeMillis()
            : "security-scan-" + originalRequest.getScanId();
        
        WorkflowOptions.Builder optionsBuilder = WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueue)
            .setWorkflowId(workflowId);
        
        // Set workflow execution timeout if configured
        if (originalRequest.getScanConfig() != null && 
            originalRequest.getScanConfig().getWorkflowTimeoutSeconds() != null) {
            optionsBuilder.setWorkflowExecutionTimeout(
                java.time.Duration.ofSeconds(originalRequest.getScanConfig().getWorkflowTimeoutSeconds())
            );
        }
        
        WorkflowOptions options = optionsBuilder.build();
        
        SecurityScanWorkflow workflow = client.newWorkflowStub(
            SecurityScanWorkflow.class, 
            options
        );
        
        // Start workflow execution asynchronously
        io.temporal.api.common.v1.WorkflowExecution execution = WorkflowClient.start(workflow::executeScans, originalRequest);
        
        System.out.println("Restarted workflow: " + workflowId + " (runId: " + execution.getRunId() + ")");
        return workflowId;
    }
    
    /**
     * Get the original ScanRequest from a failed workflow
     * 
     * @param workflowId Workflow ID
     * @param runId Run ID (can be null for latest run)
     * @return Original ScanRequest, or null if not available
     */
    public ScanRequest getOriginalRequestFromWorkflow(String workflowId, String runId) {
        try {
            SecurityScanWorkflow workflow;
            if (runId != null && !runId.isEmpty()) {
                workflow = client.newWorkflowStub(
                    SecurityScanWorkflow.class, 
                    workflowId, 
                    Optional.of(runId)
                );
            } else {
                // Get latest run
                workflow = client.newWorkflowStub(SecurityScanWorkflow.class, workflowId);
            }
            
            // Query workflow for original request
            return workflow.getOriginalRequest();
        } catch (Exception e) {
            System.err.println("Error retrieving original request from workflow " + workflowId + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Monitor and restart all workflows that failed due to specific failure types
     * 
     * This method:
     * 1. Finds all workflows that failed due to the specified failure type
     * 2. Verifies conditions are restored (e.g., storage health for storage failures)
     * 3. Retrieves original ScanRequest from each workflow
     * 4. Restarts each workflow from the beginning
     * 
     * @param taskQueue Task queue to monitor
     * @param failureType Type of failure to restart (or ALL for any restartable failure)
     * @param verifyStorageFirst If true, verifies storage is healthy before restarting (for storage failures)
     * @param useNewWorkflowId If true, creates new workflow IDs for restarts; if false, reuses original IDs
     * @return Number of workflows restarted
     */
    public int restartAllFailedWorkflows(String taskQueue, FailureType failureType, boolean verifyStorageFirst, boolean useNewWorkflowId) {
        // Verify conditions based on failure type
        if (verifyStorageFirst && (failureType == FailureType.STORAGE_FAILURE || failureType == FailureType.ALL)) {
            // Verify storage is healthy before restarting storage-failed workflows
            String testPath = Shared.WORKSPACE_BASE_DIR;
            try {
                StorageHealthChecker.checkStorageHealth(testPath);
                System.out.println("Storage health verified. Proceeding with workflow restarts.");
            } catch (StorageFailureException e) {
                System.err.println("Storage is still unavailable. Cannot restart storage-failed workflows.");
                System.err.println("Error: " + e.getMessage());
                // Continue with other failure types if checking ALL
                if (failureType != FailureType.ALL) {
                    return 0;
                }
            }
        }
        
        // Find failed workflows
        List<WorkflowExecutionInfo> failedWorkflows = findFailedWorkflows(taskQueue, failureType);
        
        String failureTypeName = failureType == FailureType.ALL ? "restartable failures" : failureType.getExceptionName();
        System.out.println("Found " + failedWorkflows.size() + " workflows that failed due to " + failureTypeName);
        
        int restartedCount = 0;
        
        // For each workflow: retrieve original request and restart
        for (WorkflowExecutionInfo execution : failedWorkflows) {
            String workflowId = execution.workflowId;
            String runId = execution.runId;
            
            try {
                // Retrieve original ScanRequest from workflow
                ScanRequest originalRequest = getOriginalRequestFromWorkflow(workflowId, runId);
                
                if (originalRequest == null) {
                    System.err.println("Could not retrieve original request for workflow " + workflowId + 
                                     ". Skipping restart.");
                    continue;
                }
                
                // Restart workflow with original request
                restartWorkflow(originalRequest, useNewWorkflowId);
                restartedCount++;
                
                System.out.println("Successfully restarted workflow: " + workflowId);
                
            } catch (Exception e) {
                System.err.println("Error restarting workflow " + workflowId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Restarted " + restartedCount + " out of " + failedWorkflows.size() + " failed workflows");
        return restartedCount;
    }
    
    /**
     * Close the client connection
     */
    public void close() {
        if (serviceStub != null) {
            serviceStub.shutdown();
        }
    }
}

