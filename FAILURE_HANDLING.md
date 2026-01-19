# Failure Handling and Recovery

## Overview

This document explains how the system handles all types of failures when running on OpenShift, including storage failures, pod failures, network issues, and resource constraints.

## Failure Types and Handling

### 1. Storage (PVC) Failures

**When It Happens**:
- PVC becomes unavailable
- Storage backend (NFS) fails
- Storage mount disconnected
- Read-only filesystem

**Detection**:
- `StorageHealthChecker` verifies storage before operations
- Activities check path accessibility
- I/O errors detected and classified

**Handling**:
1. `StorageFailureException` thrown
2. Workflow catches exception
3. Workflow marked with `workflowRestartRequired: true`
4. Workflow fails immediately (no cleanup attempted)
5. External system restarts workflow when storage restored

**Example Flow**:
```
T=0s:   Clone completes ✅
T=10s:  Storage fails ❌
T=15s:  Scan tries to access repo → StorageFailureException
T=20s:  Workflow fails, marked for restart
T=30s:  Storage restored
T=35s:  Workflow restarted → Re-clones repository
```

**Implementation**:
- `StorageHealthChecker.checkStorageHealth()` - Verifies storage accessibility
- `StorageHealthChecker.verifyPathAccessible()` - Checks specific paths
- Activities check storage before operations
- Workflow catches `StorageFailureException` separately

**Restart Process**:
- Use `WorkflowRestartClient` to find failed workflows
- Retrieve original `ScanRequest` from workflow history
- Restart workflow with original request
- New workflow re-clones repository and re-runs scans

### 2. Pod Failures

**When It Happens**:
- Pod crashes (OOMKilled, segmentation fault)
- Pod evicted (resource pressure)
- Node failure
- Liveness/readiness probe failures

**Detection**:
- Temporal detects missing heartbeats
- Kubernetes detects pod failures

**Handling**:
1. Temporal marks activity as failed
2. Temporal retries activity on new pod
3. New pod accesses same repository on shared storage
4. Activity continues (idempotent design)
5. Workflow completes successfully

**Key Points**:
- Repository persists on shared storage (PVC RWX)
- Activities are idempotent (safe to retry)
- No restart required - workflow continues

**Example Flow**:
```
T=0s:   Clone completes ✅
T=10s:  Scan starts on Pod 1
T=20s:  Pod 1 crashes 💥
T=25s:  Temporal detects failure
T=30s:  Temporal retries on Pod 2
T=35s:  Pod 2 accesses same repo ✅
T=40s:  Scan completes successfully
```

### 3. Network Failures

**When It Happens**:
- Temporal service unavailable
- Network partition
- DNS resolution failure
- Network policy blocking

**Detection**:
- Temporal SDK detects connection failures
- Heartbeat failures

**Handling**:
1. Temporal SDK automatically retries connections
2. Workflows pause (state persisted in Temporal)
3. Activities pause (heartbeats fail)
4. When network restored, workflows resume automatically
5. Activities continue from last checkpoint

**Key Points**:
- Handled automatically by Temporal SDK
- No application code needed
- Workflows persist state and resume

### 4. Resource Exhaustion

**When It Happens**:
- CPU limit exceeded (throttling)
- Memory limit exceeded (OOMKilled)
- Storage quota exceeded
- Namespace quota reached

**Detection**:
- Kubernetes detects OOMKilled
- Space checks detect quota issues
- Activity timeouts detect throttling

**Handling**:
1. **OOMKilled**: Pod restarted by Kubernetes, Temporal retries activity
2. **Storage Quota**: `InsufficientSpaceException` with extended retries (up to 2 hours)
3. **CPU Throttling**: Activity timeout handles slow execution
4. **Namespace Quota**: Pod cannot start (deployment failure)

**Space Retry Configuration**:
```java
RetryOptions spaceRetryOptions = RetryOptions.newBuilder()
    .setInitialInterval(Duration.ofMinutes(1))
    .setMaximumInterval(Duration.ofMinutes(10))
    .setBackoffCoefficient(1.5)
    .setMaximumAttempts(10)  // Up to 2 hours total
    .build();
```

### 5. Deployment Failures

**When It Happens**:
- SCC violations
- Image pull failures
- PVC mount failures
- Missing CLI tools
- Permission issues

**Detection**:
- `DeploymentHealthChecker` at worker startup
- Checks storage mount, CLI tools, permissions

**Handling**:
1. Worker startup checks deployment health
2. If unhealthy, worker exits with error code 1
3. Clear error messages indicate issue
4. Pod marked as failed (won't start)
5. Fix deployment issues and restart pod

**Health Checks**:
- Storage mount exists and is accessible
- CLI tools (git, gitleaks, detect.sh) available
- Workspace directory can be created/accessed
- Read/write permissions verified

### 6. Application Failures

**When It Happens**:
- Git clone failures
- Scan tool errors
- Invalid configuration
- Activity execution errors

**Detection**:
- Activities catch exceptions
- Process exit codes checked

**Handling**:
1. Activity catches exception
2. Creates error `ScanResult`
3. Returns error result to workflow
4. Workflow continues with error result
5. Other scans continue (if parallel)
6. Workflow completes with partial results

## Exception Types

### StorageFailureException
- **When**: Storage (PVC) unavailable
- **Action**: Workflow fails, marked for restart
- **Restart**: Required from beginning

### InsufficientSpaceException
- **When**: Not enough space for operation
- **Action**: Extended retries (up to 2 hours)
- **Restart**: Not required - retries succeed when space available

### NetworkFailureException
- **When**: Network connectivity issues
- **Action**: Temporal SDK handles automatically
- **Restart**: Not required - workflows resume

### ResourceExhaustionException
- **When**: Resource constraints
- **Action**: Pod restart, Temporal retry
- **Restart**: Not required - retries on new pod

### DeploymentFailureException
- **When**: Deployment-level issues
- **Action**: Worker fails fast at startup
- **Restart**: Fix deployment, restart pod

## Workflow Error Handling

The workflow handles different failure types appropriately:

```java
try {
    // Execute workflow steps...
} catch (StorageFailureException e) {
    // Storage failure - restart required
    summary.addMetadata("workflowRestartRequired", "true");
    throw e; // Fail workflow
} catch (NetworkFailureException e) {
    // Network failure - may be transient
    summary.addMetadata("networkFailure", "true");
    throw e; // Fail workflow
} catch (ResourceExhaustionException e) {
    // Resource exhaustion
    summary.addMetadata("resourceExhaustion", "true");
    throw e; // Fail workflow
} catch (Exception e) {
    // Other failures - handle and continue or fail
}
```

## Storage Failure Detection

### StorageHealthChecker

Utility class that checks storage health:

```java
// Check storage accessibility
StorageHealthChecker.checkStorageHealth(workspacePath);

// Verify specific path is accessible
StorageHealthChecker.verifyPathAccessible(repoPath);
```

### Failure Detection Points

Storage health checked at:
1. **Repository Cloning**: Before creating workspace, before cloning
2. **Scan Execution**: Before accessing repository path
3. **Space Checks**: Before calculating available space

### Storage Failure Indicators

Detects:
- I/O errors (`IOException`, `FileSystemException`)
- Read-only filesystem errors
- Connection errors ("transport endpoint is not connected", "stale file handle")
- Permission errors
- Mount point errors

## Workflow Restart

### When Storage Fails

1. **Workflow Fails**: Throws `StorageFailureException`
2. **Marked for Restart**: Metadata `workflowRestartRequired: true`
3. **Storage Restored**: External system detects storage is healthy
4. **Restart Workflow**: Use `WorkflowRestartClient` to restart
5. **New Execution**: Workflow re-clones repository and re-runs scans

### Restart Process

```java
WorkflowRestartClient client = new WorkflowRestartClient("temporal:7233");

// Find workflows that failed due to storage
List<WorkflowRestartClient.WorkflowExecutionInfo> failedWorkflows = client.findFailedWorkflows(
    "SECURITY_SCAN_TASK_QUEUE",
    WorkflowRestartClient.FailureType.STORAGE_FAILURE
);
List<String> failedWorkflowIds = failedWorkflows.stream()
    .map(w -> w.workflowId)
    .collect(java.util.stream.Collectors.toList());
    Shared.SECURITY_SCAN_TASK_QUEUE
);

// For each failed workflow:
// 1. Retrieve original ScanRequest from workflow history
// 2. Restart workflow with original request
ScanRequest originalRequest = retrieveOriginalRequest(workflowId);
client.restartWorkflow(originalRequest, true); // Use new workflow ID
```

## Edge Cases

### ✅ Storage Fails After Clone, Before Scan
- **Detection**: Scan activity detects storage failure
- **Handling**: Workflow fails, marked for restart
- **Result**: Workflow restarts, re-clones repository

### ✅ Storage Fails During Scan
- **Detection**: Scan activity detects storage failure
- **Handling**: Workflow fails, marked for restart
- **Result**: Workflow restarts, re-clones repository

### ✅ Multiple Pods Fail Simultaneously
- **Detection**: Temporal detects all failures
- **Handling**: Temporal retries all activities on new pods
- **Result**: All workflows continue successfully

### ✅ Network Partition
- **Detection**: Temporal SDK detects connection failures
- **Handling**: Workflows pause, resume when network restored
- **Result**: Workflows continue automatically

### ✅ Storage Quota Exceeded
- **Detection**: Space check detects insufficient space
- **Handling**: Extended retries (up to 2 hours)
- **Result**: Retries succeed when space becomes available

## Monitoring Recommendations

### Critical Alerts

1. **Storage Failures**: Alert when `StorageFailureException` thrown
2. **Deployment Failures**: Alert when worker fails to start
3. **Workflow Failures**: Monitor failure reasons and restart requirements
4. **Resource Usage**: Monitor CPU/memory usage trends

### Key Metrics

1. Storage health (PVC availability, mount status)
2. Pod health (restart rate, OOMKilled count)
3. Workflow health (success rate, failure reasons)
4. Resource usage (CPU/memory trends)
5. Network health (Temporal connectivity)

## Summary

**All failure scenarios are handled:**

✅ **Storage Failures**: Detected, workflows marked for restart
✅ **Pod Failures**: Handled by Temporal retries
✅ **Network Failures**: Handled by Temporal SDK
✅ **Resource Exhaustion**: Handled by Kubernetes + Temporal
✅ **Deployment Failures**: Detected at startup
✅ **Application Failures**: Comprehensive error handling

The system is production-ready with comprehensive failure handling for OpenShift deployment.

