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
 *   TASK_QUEUES - Comma-separated list of task queues to monitor 
 *                 (default: all scan-type queues - BLACKDUCK, DEFAULT)
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
        
        // Get task queues to monitor (comma-separated, or default to all scan-type queues)
        String taskQueuesEnv = System.getenv("TASK_QUEUES");
        String[] taskQueues;
        if (taskQueuesEnv != null && !taskQueuesEnv.isEmpty()) {
            taskQueues = taskQueuesEnv.split(",");
            for (int i = 0; i < taskQueues.length; i++) {
                taskQueues[i] = taskQueues[i].trim();
            }
        } else {
            // Default: monitor all scan-type queues (structure supports adding new scan types in the future)
            taskQueues = new String[]{
                Shared.TASK_QUEUE_BLACKDUCK,
                Shared.SECURITY_SCAN_TASK_QUEUE_DEFAULT
            };
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
        System.out.println("Task Queues: " + String.join(", ", taskQueues));
        System.out.println("Failure Type: " + failureType);
        System.out.println("Verify Storage First: " + verifyStorage);
        System.out.println("Use New Workflow IDs: " + useNewWorkflowId);
        System.out.println();
        
        WorkflowRestartClient client = null;
        
        try {
            // Create restart client
            client = new WorkflowRestartClient(temporalAddress);
            
            // Monitor all specified task queues
            int totalRestarted = 0;
            for (String taskQueue : taskQueues) {
                System.out.println("--- Monitoring queue: " + taskQueue + " ---");
                
                // Find and restart all failed workflows of the specified type in this queue
                int restartedCount = client.restartAllFailedWorkflows(
                    taskQueue,
                    failureType,
                    verifyStorage, 
                    useNewWorkflowId
                );
                
                totalRestarted += restartedCount;
                System.out.println("Restarted " + restartedCount + " workflows from queue: " + taskQueue);
                System.out.println();
            }
            
            System.out.println("=== Summary ===");
            System.out.println("Total workflows restarted: " + totalRestarted);
            System.out.println("Queues monitored: " + taskQueues.length);
            
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
