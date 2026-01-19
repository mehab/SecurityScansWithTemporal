package securityscanapp;

/**
 * Standalone service for monitoring and restarting workflows that failed due to storage failures
 * 
 * This service can be run as:
 * 1. A scheduled job (cron, Kubernetes CronJob, etc.)
 * 2. A standalone daemon process
 * 3. A manual utility script
 * 
 * Usage:
 *   java -cp target/security-scan-1.0.0.jar securityscanapp.WorkflowRestartService
 * 
 * Environment variables:
 *   TEMPORAL_ADDRESS - Temporal service address (default: localhost:7233)
 *   TASK_QUEUE - Task queue to monitor (default: SECURITY_SCAN_TASK_QUEUE)
 *   FAILURE_TYPE - Type of failures to restart: STORAGE_FAILURE, NETWORK_FAILURE, 
 *                  RESOURCE_EXHAUSTION, DEPLOYMENT_FAILURE, or ALL (default: ALL)
 *   VERIFY_STORAGE - Verify storage health before restarting (default: true)
 *   USE_NEW_WORKFLOW_ID - Use new workflow IDs for restarts (default: true)
 */
public class WorkflowRestartService {
    
    public static void main(String[] args) {
        // Get Temporal service connection
        String temporalAddress = System.getenv("TEMPORAL_ADDRESS");
        if (temporalAddress == null || temporalAddress.isEmpty()) {
            temporalAddress = "localhost:7233";
        }
        
        // Get task queue to monitor
        String taskQueue = System.getenv("TASK_QUEUE");
        if (taskQueue == null || taskQueue.isEmpty()) {
            taskQueue = Shared.SECURITY_SCAN_TASK_QUEUE_DEFAULT;
        }
        
        // Get failure type to monitor
        String failureTypeStr = System.getenv("FAILURE_TYPE");
        WorkflowRestartClient.FailureType failureType = WorkflowRestartClient.FailureType.ALL;
        if (failureTypeStr != null && !failureTypeStr.isEmpty()) {
            try {
                failureType = WorkflowRestartClient.FailureType.valueOf(failureTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid FAILURE_TYPE: " + failureTypeStr + ". Using ALL.");
                failureType = WorkflowRestartClient.FailureType.ALL;
            }
        }
        
        // Get configuration flags
        boolean verifyStorage = !"false".equalsIgnoreCase(System.getenv("VERIFY_STORAGE"));
        boolean useNewWorkflowId = !"false".equalsIgnoreCase(System.getenv("USE_NEW_WORKFLOW_ID"));
        
        System.out.println("=== Workflow Restart Service ===");
        System.out.println("Temporal Address: " + temporalAddress);
        System.out.println("Task Queue: " + taskQueue);
        System.out.println("Failure Type: " + failureType);
        System.out.println("Verify Storage First: " + verifyStorage);
        System.out.println("Use New Workflow IDs: " + useNewWorkflowId);
        System.out.println();
        
        WorkflowRestartClient client = null;
        
        try {
            // Create restart client
            client = new WorkflowRestartClient(temporalAddress);
            
            // Find and restart all failed workflows of the specified type
            int restartedCount = client.restartAllFailedWorkflows(
                taskQueue,
                failureType,
                verifyStorage, 
                useNewWorkflowId
            );
            
            System.out.println();
            System.out.println("=== Summary ===");
            System.out.println("Workflows restarted: " + restartedCount);
            
        } catch (Exception e) {
            System.err.println("Error in workflow restart service: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }
}
