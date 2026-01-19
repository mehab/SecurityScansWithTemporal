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
        
        // Get task queues to poll from environment variable (comma-separated)
        // Default: poll all queues
        String taskQueuesEnv = System.getenv("TASK_QUEUES");
        String[] taskQueues;
        if (taskQueuesEnv != null && !taskQueuesEnv.isEmpty()) {
            taskQueues = taskQueuesEnv.split(",");
            for (int i = 0; i < taskQueues.length; i++) {
                taskQueues[i] = taskQueues[i].trim();
            }
        } else {
            // Default: poll all queues
            taskQueues = new String[]{
                Shared.SECURITY_SCAN_TASK_QUEUE_DEFAULT,
                Shared.SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING,
                Shared.SECURITY_SCAN_TASK_QUEUE_PRIORITY
            };
        }
        
        // Create workers for each task queue
        for (String taskQueue : taskQueues) {
            Worker worker = factory.newWorker(taskQueue);
            
            // Register workflow implementation
            worker.registerWorkflowImplementationTypes(SecurityScanWorkflowImpl.class);
            
            // Register activity implementations
            worker.registerActivitiesImplementations(
                new RepositoryActivityImpl(),
                new GitleaksScanActivityImpl(),
                new BlackDuckScanActivityImpl(),
                new StorageActivityImpl()
            );
            
            System.out.println("Registered worker for task queue: " + taskQueue);
        }
        
        System.out.println("Security Scan Worker started");
        System.out.println("Polling task queues: " + String.join(", ", taskQueues));
        System.out.println("Workspace Base Directory: " + Shared.WORKSPACE_BASE_DIR);
        System.out.println("Worker is running and actively polling the Task Queues.");
        System.out.println("To quit, use ^C to interrupt.");
        System.out.println();
        System.out.println("Note: Set TASK_QUEUES environment variable to poll specific queues");
        System.out.println("      Example: TASK_QUEUES=SECURITY_SCAN_TASK_QUEUE,SECURITY_SCAN_TASK_QUEUE_PRIORITY");
        
        // Start all registered workers
        factory.start();
    }
}

