# Workflow Restart Client Usage Guide

## Overview

The `WorkflowRestartClient` is a utility for automatically finding and restarting workflows that failed due to various failure types. The client can handle:

1. **Storage Failures** (`StorageFailureException`): Storage unavailable, restart required when storage restored
2. **Network Failures** (`NetworkFailureException`): Network issues, restart if network not restored after retries
3. **Resource Exhaustion** (`ResourceExhaustionException`): Resource constraints, restart with adjusted limits
4. **Deployment Failures** (`DeploymentFailureException`): Deployment issues, restart after deployment fixed
5. **All Failures** (`ALL`): Restart workflows that failed due to any restartable failure type

The client can:
1. Find all workflows that failed due to specific failure types
2. Retrieve the original `ScanRequest` from each failed workflow
3. Restart each workflow from the beginning once conditions are restored

## Components

### 1. WorkflowRestartClient

The main client class that provides methods to:
- Find storage-failed workflows
- Retrieve original scan requests
- Restart workflows

### 2. WorkflowRestartService

A standalone service that can be run as:
- A scheduled job (cron, Kubernetes CronJob)
- A standalone daemon process
- A manual utility script

### 3. Workflow Query Method

The `SecurityScanWorkflow` interface now includes a `getOriginalRequest()` query method that allows retrieving the original `ScanRequest` from any workflow execution (including failed ones).

## Usage

### Option 1: Standalone Service (Recommended)

Run the `WorkflowRestartService` as a standalone application:

```bash
# Set environment variables
export TEMPORAL_ADDRESS=temporal.example.com:7233
export TASK_QUEUE=SECURITY_SCAN_TASK_QUEUE
export VERIFY_STORAGE=true
export USE_NEW_WORKFLOW_ID=true

# Run the service
java -cp target/security-scan-1.0.0.jar securityscanapp.WorkflowRestartService
```

**Environment Variables:**
- `TEMPORAL_ADDRESS`: Temporal service address (default: `localhost:7233`)
- `TASK_QUEUE`: Task queue to monitor (default: `SECURITY_SCAN_TASK_QUEUE`)
- `FAILURE_TYPE`: Type of failures to restart: `STORAGE_FAILURE`, `NETWORK_FAILURE`, `RESOURCE_EXHAUSTION`, `DEPLOYMENT_FAILURE`, or `ALL` (default: `ALL`)
- `VERIFY_STORAGE`: Verify storage health before restarting (default: `true`)
- `USE_NEW_WORKFLOW_ID`: Use new workflow IDs for restarts (default: `true`)

### Option 2: Programmatic Usage

Use `WorkflowRestartClient` in your own code:

```java
// Create client
WorkflowRestartClient client = new WorkflowRestartClient("temporal.example.com:7233");

// Find workflows by specific failure type
List<WorkflowRestartClient.WorkflowExecutionInfo> networkFailedWorkflows = 
    client.findFailedWorkflows("SECURITY_SCAN_TASK_QUEUE", 
                               WorkflowRestartClient.FailureType.NETWORK_FAILURE);

// Or find all restartable failures
List<WorkflowRestartClient.WorkflowExecutionInfo> allFailedWorkflows = 
    client.findFailedWorkflows("SECURITY_SCAN_TASK_QUEUE", 
                               WorkflowRestartClient.FailureType.ALL);

// For each failed workflow, retrieve original request and restart
for (WorkflowRestartClient.WorkflowExecutionInfo execution : failedWorkflows) {
    // Get original request
    ScanRequest originalRequest = client.getOriginalRequestFromWorkflow(
        execution.workflowId, 
        execution.runId
    );
    
    if (originalRequest != null) {
        // Restart workflow
        client.restartWorkflow(originalRequest, true); // true = use new workflow ID
    }
}

// Restart workflows by failure type
int networkRestarted = client.restartAllFailedWorkflows(
    "SECURITY_SCAN_TASK_QUEUE",
    WorkflowRestartClient.FailureType.NETWORK_FAILURE,
    false,  // no storage check needed for network failures
    true    // use new workflow IDs
);

// Or restart all restartable failures
int allRestarted = client.restartAllFailedWorkflows(
    "SECURITY_SCAN_TASK_QUEUE",
    WorkflowRestartClient.FailureType.ALL,
    true,   // verify storage (for storage failures)
    true    // use new workflow IDs
);

client.close();
```

### Option 3: Kubernetes CronJob (Recommended for Production)

Deploy as a Kubernetes CronJob to run automatically every 30 minutes:

```bash
# Apply the CronJob manifest
kubectl apply -f kubernetes-cronjob-restart-service.yaml

# Or for OpenShift
oc apply -f kubernetes-cronjob-restart-service.yaml
```

The CronJob is configured to:
- Run every 30 minutes (0.5 hours): `schedule: "*/30 * * * *"`
- Monitor all restartable failure types by default (`FAILURE_TYPE=ALL`)
- Verify storage health before restarting (for storage failures)
- Use new workflow IDs for restarts
- Keep history of last 3 successful jobs and 1 failed job
- Prevent concurrent executions
- Timeout after 10 minutes if job runs too long

See `kubernetes-cronjob-restart-service.yaml` for the complete configuration.

**Failure Type Options:**
- `ALL` (default): Restart workflows that failed due to any restartable failure type
- `STORAGE_FAILURE`: Only restart workflows that failed due to storage issues
- `NETWORK_FAILURE`: Only restart workflows that failed due to network issues
- `RESOURCE_EXHAUSTION`: Only restart workflows that failed due to resource constraints
- `DEPLOYMENT_FAILURE`: Only restart workflows that failed due to deployment issues

## How It Works

### 1. Finding Failed Workflows

The client queries Temporal for failed workflows using the `ListWorkflowExecutions` API:

```java
ListWorkflowExecutionsRequest request = ListWorkflowExecutionsRequest.newBuilder()
    .setQuery("TaskQueue = 'SECURITY_SCAN_TASK_QUEUE' AND ExecutionStatus = 'FAILED'")
    .build();
```

### 2. Identifying Failure Types

For each failed workflow, the client:
- Checks if the workflow ID matches the security scan pattern (`security-scan-*`)
- Attempts to query the workflow for the original request
- Checks workflow execution status and failure information
- Filters by failure type if specified (STORAGE_FAILURE, NETWORK_FAILURE, etc.)
- In production, you could also check workflow history events for specific exception types

**Supported Failure Types:**
- `STORAGE_FAILURE`: Storage unavailable, requires restart when storage restored
- `NETWORK_FAILURE`: Network issues, may require restart if network not restored
- `RESOURCE_EXHAUSTION`: Resource constraints, may require restart with adjusted limits
- `DEPLOYMENT_FAILURE`: Deployment issues, requires restart after deployment fixed
- `ALL`: All restartable failure types

### 3. Retrieving Original Request

The workflow's `getOriginalRequest()` query method returns the original `ScanRequest` that started the workflow. This is stored in the workflow state when the workflow starts.

### 4. Restarting Workflows

When restarting:
- Creates a new workflow execution with the original `ScanRequest`
- Optionally uses a new workflow ID (to avoid conflicts)
- Preserves the original task queue and timeout settings

## Important Notes

### Storage Health Check

The service verifies storage health before restarting workflows:

```java
StorageHealthChecker.checkStorageHealth(Shared.WORKSPACE_BASE_DIR);
```

If storage is still unavailable, the service will not restart workflows.

### Workflow ID Strategy

- **Use New Workflow IDs (`useNewWorkflowId = true`)**: Creates new workflow IDs like `security-scan-{scanId}-restart-{timestamp}`. This is safer and allows tracking restarts separately.
- **Reuse Original IDs (`useNewWorkflowId = false`)**: Reuses the original workflow ID. This will create a new run of the same workflow ID.

### Query Method Requirements

For the restart client to work, workflows must:
1. Store the original `ScanRequest` in workflow state
2. Implement the `getOriginalRequest()` query method
3. Be queryable (workflow must not be completely purged)

## Production Considerations

### 1. Enhanced Storage Failure Detection

In production, you may want to enhance storage failure detection by:
- Checking workflow history events for `StorageFailureException`
- Using search attributes to mark storage failures
- Checking workflow metadata for storage failure indicators

### 2. Rate Limiting

When restarting many workflows, consider:
- Rate limiting restarts to avoid overwhelming the system
- Batching restarts
- Adding delays between restarts

### 3. Monitoring

Monitor:
- Number of workflows found
- Number of workflows successfully restarted
- Number of workflows that failed to restart
- Storage health status

### 4. Error Handling

The service handles:
- Workflows that can't be queried (skips them)
- Workflows without original requests (skips them)
- Storage still unavailable (exits without restarting)
- Individual workflow restart failures (continues with others)

## Example Output

```
=== Workflow Restart Service ===
Temporal Address: temporal.example.com:7233
Task Queue: SECURITY_SCAN_TASK_QUEUE
Verify Storage First: true
Use New Workflow IDs: true

Storage health verified. Proceeding with workflow restarts.
Found 3 workflows that failed due to storage
Restarted workflow: security-scan-12345-restart-1705658400000 (runId: abc123...)
Successfully restarted workflow: security-scan-12345
Restarted workflow: security-scan-67890-restart-1705658401000 (runId: def456...)
Successfully restarted workflow: security-scan-67890
Restarted workflow: security-scan-11111-restart-1705658402000 (runId: ghi789...)
Successfully restarted workflow: security-scan-11111

=== Summary ===
Workflows restarted: 3
```

## Troubleshooting

### No Workflows Found

- Check that workflows actually failed with storage failures
- Verify the task queue name is correct
- Check Temporal service connectivity

### Cannot Query Workflow

- Workflow may have been purged
- Workflow may not have started (no original request stored)
- Check Temporal permissions

### Storage Still Unavailable

- The service will not restart workflows if storage is unavailable
- Fix storage issues first, then run the service again

### Restart Failures

- Check that the original `ScanRequest` is valid
- Verify task queue exists and is accessible
- Check Temporal service logs for errors
