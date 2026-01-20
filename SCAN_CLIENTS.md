# Scan Orchestrator Clients

## Overview

The security scanning application provides dedicated orchestrator clients for each scan type. Each client is responsible for:

- Validating scan requests for its specific scan type
- Routing requests to the appropriate task queue
- Managing workflow execution
- Providing type-safe APIs

## Available Clients

### 1. BlackDuckScanClient

Dedicated client for BlackDuck Detect scans.

**Features:**
- Only accepts `BLACKDUCK_DETECT` scan requests
- Routes to `SECURITY_SCAN_TASK_QUEUE_BLACKDUCK`
- Provides helper methods for creating scan requests

**Usage:**

```java
// Create client
BlackDuckScanClient client = new BlackDuckScanClient("temporal-service:7233");

// Create scan request
ScanRequest request = client.createScanRequest(
    "app-123",           // Application ID
    "api-component",     // Component name
    "build-456",         // Build ID
    "https://github.com/example/repo.git",
    "main",              // Branch
    "abc123def456"       // Commit SHA
);

// Configure scan settings
ScanConfig config = new ScanConfig();
config.setBlackduckApiToken("token");
config.setBlackduckUrl("https://blackduck.example.com");
request.setScanConfig(config);

// Submit scan asynchronously
String workflowId = client.submitScan(request);

// Or wait for completion
ScanSummary summary = client.submitScanAndWait(request);
```

## Client Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    BaseScanClient                        │
│  (Abstract base class with common functionality)         │
│                                                           │
│  • submitScan(ScanRequest)                               │
│  • submitScanAndWait(ScanRequest)                       │
│  • Validates scan type                                   │
│  • Routes to appropriate queue                           │
│  • Generates workflow ID                                 │
└──────────────┬──────────────────────────┬───────────────┘
               │                          │
               │                          │
    ┌──────────▼──────────┐
    │ BlackDuckScanClient  │
    │                      │
    │ • BLACKDUCK_DETECT   │
    │ • Routes to:         │
    │   BLACKDUCK queue    │
    └──────────────────────┘
```

## Benefits

### 1. Type Safety

Each client only accepts requests for its specific scan type:

```java
// ✅ Valid - BlackDuck client accepts BlackDuck requests
BlackDuckScanClient bdClient = new BlackDuckScanClient();
ScanRequest bdRequest = bdClient.createScanRequest(...);
bdRequest.setToolType(ScanType.BLACKDUCK_DETECT); // Already set by createScanRequest
bdClient.submitScan(bdRequest); // ✅ Works

// ❌ Invalid - BlackDuck client only accepts BlackDuck requests
// Other scan types are not currently supported
```

### 2. Separation of Concerns

Different teams/systems can use different clients:

- **Compliance Team**: Uses `BlackDuckScanClient` for license scanning
- **CI/CD Pipeline**: Can use either client based on pipeline stage

### 3. Independent Deployment

Each client can be deployed as a separate service:

```yaml
# Deployment for BlackDuck orchestrator
apiVersion: apps/v1
kind: Deployment
metadata:
  name: blackduck-scan-orchestrator
spec:
  containers:
  - name: orchestrator
    image: blackduck-scan-client:latest
    env:
    - name: TEMPORAL_ADDRESS
      value: "temporal-service:7233"
```

### 4. Queue Routing

Each client automatically routes to the correct queue:

- `BlackDuckScanClient` → `SECURITY_SCAN_TASK_QUEUE_BLACKDUCK`

This ensures requests are picked up by the appropriate workers.

## Integration Examples

### Example 1: CI/CD Pipeline Integration

```java
// In your CI/CD pipeline (Jenkins, GitLab CI, etc.)
public class PipelineScanTrigger {
    
    public void triggerBlackDuckScan(String appId, String component, String buildId) {
        BlackDuckScanClient client = new BlackDuckScanClient();
        
        ScanRequest request = client.createScanRequest(
            appId,
            component,
            buildId,
            getRepositoryUrl(),
            getBranch(),
            getCommitSha()
        );
        
        // Submit asynchronously - don't block pipeline
        String workflowId = client.submitScan(request);
        
        // Store workflowId for later status checking
        storeWorkflowId(buildId, workflowId);
    }
}
```

### Example 2: REST API Integration

```java
@RestController
@RequestMapping("/api/scans")
public class ScanController {
    
    private final BlackDuckScanClient blackDuckClient;
    
    @PostMapping("/blackduck")
    public ResponseEntity<String> triggerBlackDuckScan(@RequestBody ScanRequestDTO dto) {
        ScanRequest request = blackDuckClient.createScanRequest(
            dto.getAppId(),
            dto.getComponent(),
            dto.getBuildId(),
            dto.getRepositoryUrl(),
            dto.getBranch(),
            dto.getCommitSha()
        );
        
        String workflowId = blackDuckClient.submitScan(request);
        return ResponseEntity.ok(workflowId);
    }
    
}
```

### Example 3: Event-Driven Integration

```java
// Listen to build events and trigger appropriate scans
@Component
public class BuildEventListener {
    
    private final BlackDuckScanClient blackDuckClient;
    @EventListener
    public void onBuildComplete(BuildCompleteEvent event) {
        // Trigger BlackDuck scan for license compliance
        ScanRequest bdRequest = blackDuckClient.createScanRequest(
            event.getAppId(),
            event.getComponent(),
            event.getBuildId(),
            event.getRepositoryUrl(),
            event.getBranch(),
            event.getCommitSha()
        );
        blackDuckClient.submitScan(bdRequest);
    }
}
```

## Configuration

### Environment Variables

- `TEMPORAL_ADDRESS`: Temporal service address (default: `localhost:7233`)

### Client Initialization

```java
// Use environment variable
BlackDuckScanClient client = new BlackDuckScanClient(); // Uses TEMPORAL_ADDRESS env var

// Or specify explicitly
BlackDuckScanClient client = new BlackDuckScanClient("temporal-service:7233");
```

## Error Handling

Clients validate scan types and throw `IllegalArgumentException` for invalid requests:

```java
try {
    BlackDuckScanClient client = new BlackDuckScanClient();
    ScanRequest request = new ScanRequest(..., ScanType.BLACKDUCK_DETECT, ...);
    client.submitScan(request); // ❌ Throws IllegalArgumentException
} catch (IllegalArgumentException e) {
    // Handle: "This client only supports BLACKDUCK_DETECT scans"
}
```

## Best Practices

1. **Use Type-Specific Clients**: Prefer dedicated clients over generic `SecurityScanClient`
2. **Deploy Separately**: Deploy each orchestrator as a separate service for better isolation
3. **Async Submission**: Use `submitScan()` for non-blocking submission in high-throughput scenarios
4. **Sync When Needed**: Use `submitScanAndWait()` when you need immediate results
5. **Resource Cleanup**: Always call `client.shutdown()` when done

## Migration from Generic Client

If you're currently using `SecurityScanClient`, migrate to type-specific clients:

**Before:**
```java
SecurityScanClient client = new SecurityScanClient();
ScanRequest request = new ScanRequest(..., ScanType.BLACKDUCK_DETECT, ...);
client.submitScan(request);
```

**After:**
```java
BlackDuckScanClient client = new BlackDuckScanClient();
ScanRequest request = client.createScanRequest(...);
client.submitScan(request);
```

Benefits:
- Type safety
- Clearer intent
- Better separation of concerns
- Easier to deploy independently
