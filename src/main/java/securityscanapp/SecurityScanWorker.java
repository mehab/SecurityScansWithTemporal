package securityscanapp;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * Worker that processes security scanning workflows and activities
 * This worker should run on Kubernetes pods separate from the Temporal service
 */
public class SecurityScanWorker {
    
    public static void main(String[] args) {
        // Get Temporal service connection
        // In production, this should connect to your Temporal cluster
        // For Kubernetes, use environment variables or service discovery
        String temporalAddress = System.getenv("TEMPORAL_ADDRESS");
        if (temporalAddress == null || temporalAddress.isEmpty()) {
            temporalAddress = "localhost:7233"; // Default for local development
        }
        
        System.out.println("Connecting to Temporal service at: " + temporalAddress);
        
        // Verify deployment health before starting worker
        try {
            System.out.println("Verifying deployment health...");
            DeploymentHealthChecker.verifyDeploymentHealth();
            System.out.println("Deployment health verified successfully");
        } catch (DeploymentFailureException e) {
            System.err.println("CRITICAL: Deployment health check failed!");
            System.err.println("Failure Type: " + e.getFailureType());
            System.err.println("Failure Reason: " + e.getFailureReason());
            System.err.println("Worker cannot start. Please fix deployment issues:");
            System.err.println("  - Check PVC mount and permissions");
            System.err.println("  - Verify CLI tools are installed");
            System.err.println("  - Check SCC and RBAC permissions");
            System.err.println("  - Verify storage class supports RWX");
            System.exit(1);
        }
        
        // Create Workflow service stub
        WorkflowServiceStubs serviceStub = WorkflowServiceStubs.newServiceStubs(
            io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
                .setTarget(temporalAddress)
                .build()
        );
        
        // Create Workflow client
        WorkflowClient client = WorkflowClient.newInstance(serviceStub);
        
        // Create Worker factory
        WorkerFactory factory = WorkerFactory.newInstance(client);
        
        // Get scan type (tool type) for this worker from environment variable
        // Each worker is dedicated to a specific scan type
        String scanTypeEnv = System.getenv("SCAN_TYPE");
        String taskQueue;
        
        if (scanTypeEnv != null && !scanTypeEnv.isEmpty()) {
            // Worker is configured for a specific scan type
            try {
                ScanType scanType = ScanType.valueOf(scanTypeEnv.toUpperCase());
                taskQueue = Shared.getTaskQueueForScanType(scanType);
                System.out.println("Worker configured for scan type: " + scanType);
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid SCAN_TYPE: " + scanTypeEnv);
                System.err.println("Valid values: BLACKDUCK_DETECT");
                System.err.println("Falling back to default queue");
                taskQueue = Shared.SECURITY_SCAN_TASK_QUEUE_DEFAULT;
            }
        } else {
            // Fallback: Use TASK_QUEUE environment variable or default
            String taskQueueEnv = System.getenv("TASK_QUEUE");
            if (taskQueueEnv != null && !taskQueueEnv.isEmpty()) {
                taskQueue = taskQueueEnv;
            } else {
                // Default: poll all scan-type queues
                taskQueue = null; // Will create workers for all scan types
            }
        }
        
        if (taskQueue != null) {
            // Single queue worker
            Worker worker = factory.newWorker(taskQueue);
            registerWorkerComponents(worker);
            System.out.println("Security Scan Worker started");
            System.out.println("Task Queue: " + taskQueue);
        } else {
            // Poll all scan-type queues (structure supports adding new scan types in the future)
            String[] taskQueues = new String[]{
                Shared.TASK_QUEUE_BLACKDUCK,
                Shared.SECURITY_SCAN_TASK_QUEUE_DEFAULT  // Fallback
            };
            
            for (String queue : taskQueues) {
                Worker worker = factory.newWorker(queue);
                registerWorkerComponents(worker);
                System.out.println("Registered worker for task queue: " + queue);
            }
            
            System.out.println("Security Scan Worker started");
            System.out.println("Polling task queues: " + String.join(", ", taskQueues));
        }
        
        System.out.println("Workspace Base Directory: " + Shared.WORKSPACE_BASE_DIR);
        System.out.println("Worker is running and actively polling the Task Queue(s).");
        System.out.println("To quit, use ^C to interrupt.");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  - Set SCAN_TYPE environment variable to dedicate worker to a scan type");
        System.out.println("    Example: SCAN_TYPE=BLACKDUCK_DETECT");
        System.out.println("  - Or set TASK_QUEUE to specify a custom queue");
        System.out.println("    Example: TASK_QUEUE=SECURITY_SCAN_TASK_QUEUE_BLACKDUCK");
        
        // Start all registered workers
        factory.start();
    }
    
    /**
     * Register workflow and activity implementations with a worker
     */
    private static void registerWorkerComponents(Worker worker) {
        // Register workflow implementation
        worker.registerWorkflowImplementationTypes(SecurityScanWorkflowImpl.class);
        
        // Register activity implementations
        worker.registerActivitiesImplementations(
            new RepositoryActivityImpl(),
            new BlackDuckScanActivityImpl(),
            new StorageActivityImpl()
        );
    }
}

