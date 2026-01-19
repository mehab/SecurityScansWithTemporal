# Automatic Task Queue Routing

## Overview

The security scanning application automatically routes scans to appropriate task queues based on scan characteristics. This prevents long-running scans from blocking normal scans and allows priority scans to be processed faster.

## How It Works

### Queue Selection Logic

When a scan is initiated, the system determines the task queue using this priority order:

1. **Explicit Queue Setting**: If `config.setTaskQueue()` is explicitly called, that queue is used
2. **Priority-Based**: If `config.setPriority(ScanPriority.HIGH)` is set, uses priority queue
3. **Timeout-Based**: If scan timeout > 30 minutes OR workflow timeout > 1 hour, uses long-running queue
4. **Default**: Uses the default queue

### Available Queues

- **`SECURITY_SCAN_TASK_QUEUE_DEFAULT`**: Normal scans (default)
- **`SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING`**: Scans expected to take > 30 minutes
- **`SECURITY_SCAN_TASK_QUEUE_PRIORITY`**: High-priority scans

## Usage Examples

### Example 1: High-Priority Scan

```java
ScanConfig config = new ScanConfig();
config.setPriority(ScanPriority.HIGH);  // Automatically routes to priority queue

ScanRequest request = new ScanRequest(...);
request.setScanConfig(config);

// Scan will be routed to SECURITY_SCAN_TASK_QUEUE_PRIORITY
SecurityScanWorkflow workflow = client.newWorkflowStub(...);
workflow.executeScans(request);
```

### Example 2: Long-Running Scan (Automatic Detection)

```java
ScanConfig config = new ScanConfig();
config.setScanTimeoutSeconds(3600);  // 1 hour timeout (> 30 min)
// Automatically routes to long-running queue

ScanRequest request = new ScanRequest(...);
request.setScanConfig(config);

// Scan will be routed to SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING
```

### Example 3: Explicit Queue Selection

```java
ScanConfig config = new ScanConfig();
config.setTaskQueue(Shared.SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING);
// Explicitly set queue (overrides automatic routing)

ScanRequest request = new ScanRequest(...);
request.setScanConfig(config);

// Scan will use the explicitly set queue
```

### Example 4: Normal Scan (Default)

```java
ScanConfig config = new ScanConfig();
// No priority, no long timeout -> uses default queue

ScanRequest request = new ScanRequest(...);
request.setScanConfig(config);

// Scan will be routed to SECURITY_SCAN_TASK_QUEUE_DEFAULT
```

## Worker Configuration

### Option 1: Single Worker Polling All Queues (Default)

By default, workers poll all queues. This is the simplest setup:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: security-scan-worker
spec:
  replicas: 5
  template:
    spec:
      containers:
      - name: worker
        env:
        # If TASK_QUEUES is not set, worker polls all queues by default
        - name: TEMPORAL_ADDRESS
          value: "temporal-service:7233"
```

**Benefits**:
- Simple setup
- Single deployment
- Workers automatically handle all queue types

**Drawbacks**:
- Cannot scale queues independently
- All workers share resources

### Option 2: Separate Workers for Each Queue

Deploy dedicated workers for specific queues:

```yaml
# Worker for default queue
apiVersion: apps/v1
kind: Deployment
metadata:
  name: security-scan-worker
spec:
  replicas: 5
  template:
    spec:
      containers:
      - name: worker
        env:
        - name: TASK_QUEUES
          value: "SECURITY_SCAN_TASK_QUEUE"
---
# Worker for long-running queue
apiVersion: apps/v1
kind: Deployment
metadata:
  name: security-scan-worker-long-running
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: worker
        env:
        - name: TASK_QUEUES
          value: "SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING"
---
# Worker for priority queue
apiVersion: apps/v1
kind: Deployment
metadata:
  name: security-scan-worker-priority
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: worker
        env:
        - name: TASK_QUEUES
          value: "SECURITY_SCAN_TASK_QUEUE_PRIORITY"
```

**Benefits**:
- Independent scaling per queue type
- Different resource allocations per queue
- Better isolation

**Drawbacks**:
- More complex deployment
- More pods to manage

### Option 3: Custom Queue Selection

Poll specific queues:

```yaml
env:
- name: TASK_QUEUES
  value: "SECURITY_SCAN_TASK_QUEUE,SECURITY_SCAN_TASK_QUEUE_PRIORITY"
```

This worker will only poll the default and priority queues.

## Queue Selection Rules

### Long-Running Queue

A scan is routed to the long-running queue if:
- `scanTimeoutSeconds > 1800` (30 minutes), OR
- `workflowTimeoutSeconds > 3600` (1 hour), OR
- Multiple scan types (≥3) with parallel execution enabled

### Priority Queue

A scan is routed to the priority queue if:
- `priority == ScanPriority.HIGH`

### Default Queue

A scan uses the default queue if:
- No explicit queue is set, AND
- Priority is not HIGH, AND
- Timeouts don't indicate long-running scan

## Implementation Details

### Queue Determination

The queue is determined in `SecurityScanClient.determineTaskQueue()`:

```java
private static String determineTaskQueue(ScanRequest request) {
    ScanConfig config = request.getScanConfig();
    
    // 1. Explicit queue setting
    if (config != null && config.getTaskQueue() != null) {
        return config.getTaskQueue();
    }
    
    // 2. Priority-based
    if (config != null && config.getPriority() == ScanPriority.HIGH) {
        return Shared.SECURITY_SCAN_TASK_QUEUE_PRIORITY;
    }
    
    // 3. Timeout-based
    if (isLongRunning(config)) {
        return Shared.SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING;
    }
    
    // 4. Default
    return Shared.SECURITY_SCAN_TASK_QUEUE_DEFAULT;
}
```

### Worker Polling

Workers poll queues based on the `TASK_QUEUES` environment variable:

- **Not set**: Polls all queues (default, long-running, priority)
- **Set**: Polls only the specified queues (comma-separated)

## Best Practices

1. **Use Priority for Important Scans**: Set `priority = HIGH` for time-sensitive scans
2. **Set Appropriate Timeouts**: Long timeouts automatically route to long-running queue
3. **Deploy Separate Workers**: For production, deploy separate workers for better isolation
4. **Monitor Queue Depths**: Track queue depths to identify bottlenecks
5. **Scale Workers Appropriately**: Scale priority queue workers more aggressively

## Troubleshooting

### Scan Not Using Expected Queue

- Check if `taskQueue` is explicitly set (overrides automatic routing)
- Verify timeout values (must be > 30 min for long-running)
- Check priority setting (must be HIGH for priority queue)

### Workers Not Picking Up Scans

- Verify `TASK_QUEUES` environment variable includes the queue
- Check worker logs for queue registration
- Ensure workers are running and connected to Temporal

### Queue Not Polled

- Default: Workers poll all queues if `TASK_QUEUES` is not set
- Custom: Set `TASK_QUEUES` to include the desired queues
- Separate: Deploy dedicated workers for each queue
