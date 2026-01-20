# Automatic Task Queue Routing

## Overview

The security scanning application automatically routes scans to appropriate task queues based on scan type (tool type). Each scan type has its own dedicated queue, ensuring proper isolation and independent scaling.

## How It Works

### Queue Selection Logic

When a scan is initiated, the system determines the task queue using this priority order:

1. **Explicit Queue Setting**: If `config.setTaskQueue()` is explicitly called, that queue is used
2. **Scan Type-Based**: Automatically routes to the queue for the scan type (tool type)
3. **Default**: Uses the default queue (fallback only)

### Available Queues

- **`SECURITY_SCAN_TASK_QUEUE_BLACKDUCK`**: BlackDuck Detect scans
- **`SECURITY_SCAN_TASK_QUEUE_DEFAULT`**: Default fallback queue (used if scan type is not recognized)

## Usage Examples

### Example 1: BlackDuck Scan (Automatic Routing)

```java
BlackDuckScanClient client = new BlackDuckScanClient();
ScanRequest request = client.createScanRequest(
    "app-123",
    "api-component",
    "build-456",
    "https://github.com/example/repo.git",
    "main",
    "abc123def456"
);

// Automatically routes to SECURITY_SCAN_TASK_QUEUE_BLACKDUCK
String workflowId = client.submitScan(request);
```

### Example 3: Explicit Queue Selection

```java
ScanConfig config = new ScanConfig();
config.setTaskQueue("CUSTOM_QUEUE_NAME");  // Override automatic routing

ScanRequest request = new ScanRequest(...);
request.setScanConfig(config);

// Uses explicitly set queue
```

## Worker Configuration

### Option 1: Scan-Type Specific Workers (Recommended)

Each scan type has dedicated workers:

```yaml
# Worker for BlackDuck
apiVersion: apps/v1
kind: Deployment
metadata:
  name: security-scan-worker-blackduck
spec:
  template:
    spec:
      containers:
      - name: worker
        env:
        - name: SCAN_TYPE
          value: "BLACKDUCK_DETECT"
        # Polls SECURITY_SCAN_TASK_QUEUE_BLACKDUCK
```

**Benefits**:
- Clear isolation between scan types
- Independent scaling per scan type
- Different resource allocations per scan type

### Option 2: Multi-Queue Worker (Fallback)

A single worker can poll multiple queues:

```yaml
env:
# If SCAN_TYPE is not set, worker polls all scan-type queues
```

**Benefits**:
- Simple setup
- Single deployment
- Workers automatically handle all queue types

**Drawbacks**:
- Cannot scale queues independently
- All workers share resources

## Queue Selection Rules

### BlackDuck Queue

A scan is routed to the BlackDuck queue if:
- Tool type is `BLACKDUCK_DETECT`

### Default Queue

A scan uses the default queue if:
- Scan type is not recognized (fallback only)

## Implementation Details

### Queue Determination

The queue is determined in `BaseScanClient` and `Shared.getTaskQueueForScanType()`:

```java
// In BaseScanClient
String taskQueue = Shared.getTaskQueueForScanType(supportedScanType);

// In Shared.java
static String getTaskQueueForScanType(ScanType scanType) {
    if (scanType == null) {
        return SECURITY_SCAN_TASK_QUEUE_DEFAULT;
    }
    
    switch (scanType) {
        case BLACKDUCK_DETECT:
            return TASK_QUEUE_BLACKDUCK;
        default:
            return SECURITY_SCAN_TASK_QUEUE_DEFAULT;
    }
}
```

### Worker Polling

Workers poll queues based on the `SCAN_TYPE` environment variable:

- **Set to specific scan type**: Polls only that scan type's queue
- **Not set**: Polls all scan-type queues (BlackDuck, Default)

## Best Practices

1. **Use Scan-Type Specific Clients**: Use `BlackDuckScanClient` for automatic routing
2. **Deploy Separate Workers**: For production, deploy separate workers for each scan type
3. **Monitor Queue Depths**: Track queue depths to identify bottlenecks
4. **Scale Workers Appropriately**: Scale workers based on scan type workload

## Troubleshooting

### Scan Not Using Expected Queue

- Check if `taskQueue` is explicitly set (overrides automatic routing)
- Verify tool type is correctly set in `ScanRequest`
- Check client type matches scan type

### Workers Not Picking Up Scans

- Verify `SCAN_TYPE` environment variable matches the queue
- Check worker logs for queue registration
- Ensure workers are running and connected to Temporal

### Queue Not Polled

- Default: Workers poll all queues if `SCAN_TYPE` is not set
- Specific: Set `SCAN_TYPE` to poll only that scan type's queue
- Separate: Deploy dedicated workers for each scan type
