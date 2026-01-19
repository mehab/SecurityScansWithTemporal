# Preventing Long-Running Scan Bottlenecks

## Problem

When a scan takes a very long time, it can create a bottleneck in the Temporal task queue, preventing other workflows from executing. This happens because:

1. **Single Task Queue**: All workflows use the same task queue by default
2. **Long Activity Timeouts**: Activities can run for up to 30 minutes (default)
3. **No Workflow Timeout**: Workflows can run indefinitely
4. **Worker Pool Saturation**: Long-running activities occupy worker threads

## Solutions

### 1. Configurable Activity Timeouts

Set shorter timeouts for scans that should complete quickly:

```java
ScanConfig config = new ScanConfig();
// Set per-scan timeout (default is 30 minutes)
config.setScanTimeoutSeconds(900); // 15 minutes
```

**Benefits**:
- Activities fail faster if they take too long
- Frees up worker threads sooner
- Allows retries or alternative strategies

**Use Cases**:
- Small repositories that should scan quickly
- Time-sensitive scans
- When you want to fail fast

### 2. Workflow Execution Timeout

Set a maximum time for the entire workflow:

```java
ScanConfig config = new ScanConfig();
// Set workflow execution timeout (fails entire workflow if exceeded)
config.setWorkflowTimeoutSeconds(3600); // 1 hour total
```

**Benefits**:
- Prevents workflows from running indefinitely
- Frees up resources when workflows exceed expected time
- Can be set at workflow start via WorkflowOptions

**Use Cases**:
- When you have strict SLA requirements
- To prevent runaway workflows
- When combined with retry logic

**Note**: Workflow timeout is set via `WorkflowOptions` at workflow start:

```java
WorkflowOptions options = WorkflowOptions.newBuilder()
    .setTaskQueue(taskQueue)
    .setWorkflowId(workflowId)
    .setWorkflowExecutionTimeout(Duration.ofSeconds(3600)) // 1 hour
    .build();
```

### 3. Separate Task Queues (Automatic Routing)

The system automatically routes scans to appropriate task queues based on:
- **Priority**: HIGH priority scans → priority queue
- **Timeout**: Long timeouts (>30 min scan or >1 hour workflow) → long-running queue
- **Explicit setting**: Manually set queue in config

**Automatic Queue Selection**:

```java
ScanConfig config = new ScanConfig();

// Option 1: Set priority (HIGH -> priority queue)
config.setPriority(ScanPriority.HIGH);  // Automatically uses priority queue

// Option 2: Set long timeout (automatically uses long-running queue)
config.setScanTimeoutSeconds(3600);  // > 30 min -> long-running queue
config.setWorkflowTimeoutSeconds(7200);  // > 1 hour -> long-running queue

// Option 3: Explicitly set queue
config.setTaskQueue(Shared.SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING);
```

**Queue Selection Priority**:
1. Explicitly set task queue (if `config.setTaskQueue()` is called)
2. Priority-based: HIGH priority → priority queue
3. Timeout-based: Long timeouts → long-running queue
4. Default: Normal queue

**Task Queue Options** (defined in `Shared.java`):
- `SECURITY_SCAN_TASK_QUEUE_DEFAULT`: Default queue for normal scans
- `SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING`: For scans expected to take > 30 minutes
- `SECURITY_SCAN_TASK_QUEUE_PRIORITY`: For high-priority scans

**Worker Configuration**:

Workers can poll multiple queues or you can deploy separate workers:

```yaml
# Worker for normal scans
apiVersion: apps/v1
kind: Deployment
metadata:
  name: security-scan-worker
spec:
  replicas: 5  # More workers for normal scans
  template:
    spec:
      containers:
      - name: worker
        env:
        - name: TASK_QUEUE
          value: "SECURITY_SCAN_TASK_QUEUE"  # Default queue
---
# Worker for long-running scans
apiVersion: apps/v1
kind: Deployment
metadata:
  name: security-scan-worker-long-running
spec:
  replicas: 2  # Fewer workers for long-running scans
  template:
    spec:
      containers:
      - name: worker
        env:
        - name: TASK_QUEUE
          value: "SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING"  # Long-running queue
```

### 4. Activity Heartbeats

Activities should send heartbeats to indicate progress:

```java
// In activity implementation
ActivityExecutionContext context = Activity.getExecutionContext();
context.heartbeat("Scanning in progress...");
```

**Benefits**:
- Temporal detects stuck activities faster
- Can cancel long-running activities
- Provides visibility into activity progress

**Heartbeat Timeout**: Currently set to 60 seconds for scan activities.

### 5. Worker Pool Scaling

Scale workers horizontally to handle more concurrent workflows:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: security-scan-worker
spec:
  replicas: 10  # Scale based on queue depth
```

**Benefits**:
- More workers = more concurrent workflows
- Can handle bursts of scan requests
- Isolates failures

**Scaling Strategy**:
- Monitor queue depth
- Scale workers based on queue size
- Use Kubernetes HPA (Horizontal Pod Autoscaler)

### 6. Activity Cancellation

Allow cancellation of long-running activities:

```java
// In activity implementation
ActivityExecutionContext context = Activity.getExecutionContext();

try {
    // Check for cancellation periodically
    while (scanning) {
        context.heartbeat("Scanning...");
        
        // Check if cancelled
        if (context.isCancelled()) {
            throw new ActivityCancelledException("Scan cancelled by user");
        }
        
        // Continue scanning...
    }
} catch (ActivityCancelledException e) {
    // Clean up and exit
    cleanup();
    throw e;
}
```

**Benefits**:
- Can cancel stuck or unwanted scans
- Frees up resources immediately
- Provides user control

## Recommended Strategies

### Strategy 1: Timeout-Based (Recommended for Most Cases)

```java
ScanConfig config = new ScanConfig();
// Set reasonable timeouts
config.setScanTimeoutSeconds(900); // 15 minutes per scan
config.setWorkflowTimeoutSeconds(3600); // 1 hour total
```

**Best For**:
- Most production scenarios
- When you want predictable behavior
- When scans should complete within known timeframes

### Strategy 2: Queue Separation (Recommended for Mixed Workloads)

```java
// For normal scans (automatic - no config needed)
ScanConfig normalConfig = new ScanConfig();
// Uses default queue automatically

// For high-priority scans
ScanConfig priorityConfig = new ScanConfig();
priorityConfig.setPriority(ScanPriority.HIGH);  // Automatically uses priority queue

// For long-running scans
ScanConfig longRunningConfig = new ScanConfig();
longRunningConfig.setScanTimeoutSeconds(3600);  // > 30 min -> automatically uses long-running queue
// OR explicitly:
// longRunningConfig.setTaskQueue(Shared.SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING);
```

**Best For**:
- Mixed workloads (fast and slow scans)
- When you need isolation
- When you have different SLA requirements

### Strategy 3: Combined Approach (Recommended for Production)

```java
ScanConfig config = new ScanConfig();

// Set timeouts
config.setScanTimeoutSeconds(900); // 15 minutes
config.setWorkflowTimeoutSeconds(3600); // 1 hour

// Use appropriate queue based on expected duration
if (expectedDuration > 30 * 60) { // > 30 minutes
    config.setScanTimeoutSeconds((int)expectedDuration);  // Automatically routes to long-running queue
} else {
    // Uses default queue automatically
}

// OR set priority for high-priority scans
if (isHighPriority) {
    config.setPriority(ScanPriority.HIGH);  // Automatically routes to priority queue
}
```

**Best For**:
- Production environments
- When you need both isolation and timeouts
- When you have diverse scan requirements

## Monitoring

### Key Metrics to Monitor

1. **Queue Depth**: Number of workflows waiting in queue
2. **Activity Duration**: How long activities take to complete
3. **Workflow Duration**: Total workflow execution time
4. **Timeout Rate**: How often timeouts occur
5. **Worker Utilization**: How busy workers are

### Alerts

Set up alerts for:
- Queue depth > threshold (e.g., > 50 workflows)
- Average activity duration > threshold (e.g., > 20 minutes)
- Timeout rate > threshold (e.g., > 10% of scans)
- Worker utilization > threshold (e.g., > 80%)

## Example: Preventing Bottleneck

### Scenario: Large Repository Taking Too Long

**Problem**: A 4GB repository scan is taking 45 minutes, blocking other scans.

**Solution**:

```java
ScanConfig config = new ScanConfig();

// 1. Set shorter timeout for this scan
config.setScanTimeoutSeconds(1800); // 30 minutes (default)

// 2. Use long-running queue to isolate
config.setTaskQueue(Shared.SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING);

// 3. Set workflow timeout as safety net
config.setWorkflowTimeoutSeconds(5400); // 90 minutes total

// 4. Use space-efficient strategies to speed up scan
config.setCloneStrategy(CloneStrategy.SHALLOW_SINGLE_BRANCH);
config.setUseSparseCheckout(true);
config.addSparseCheckoutPath("src/");

// 5. Use separate queue to isolate long-running scan
config.setTaskQueue(Shared.SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING);
```

**Result**:
- Scan runs in isolated queue (doesn't block normal scans)
- Fails after 30 minutes if scan tool is stuck
- Workflow fails after 90 minutes if workflow is stuck
- Faster scan due to space-efficient cloning

## Best Practices

1. **Set Realistic Timeouts**: Based on repository size and scan type
2. **Use Separate Queues**: For different scan types or priorities
3. **Monitor Queue Depth**: Scale workers when queue grows
4. **Implement Heartbeats**: In all long-running activities
5. **Support Cancellation**: Allow users to cancel stuck scans
6. **Log Timeout Events**: Track when and why timeouts occur
7. **Retry Strategy**: Configure retries for transient failures
8. **Resource Limits**: Set CPU/memory limits to prevent resource exhaustion

## Summary

**To prevent bottlenecks from long-running scans:**

1. ✅ **Set Activity Timeouts**: Fail activities that take too long
2. ✅ **Set Workflow Timeouts**: Fail workflows that exceed expected time
3. ✅ **Use Separate Task Queues**: Isolate long-running scans
4. ✅ **Scale Workers**: Add more workers to handle load
5. ✅ **Monitor Metrics**: Track queue depth and activity duration
6. ✅ **Implement Heartbeats**: Detect stuck activities faster
7. ✅ **Support Cancellation**: Allow manual cancellation of stuck scans

The system now supports all these strategies through configurable timeouts and task queues.

