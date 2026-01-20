# Application Structure and Scan Request Format

## Overview

The security scanning application is structured to support multi-application, multi-component scanning with unique build identifiers. Each scan request is associated with:

- **Application ID (appId)**: Identifies the application being scanned
- **Component**: Component within the application
- **Build ID (buildId)**: Unique build identifier for the component
- **Tool Type**: Single scan type (Gitleaks, BlackDuck, etc.)

## Scan Request Structure

### Structure

```java
ScanRequest request = new ScanRequest(
    "app-123",                    // Application ID
    "api-component",              // Component name
    "build-456",                  // Build ID (unique per component build)
    ScanType.GITLEAKS_SECRETS,    // Tool type (single scan type)
    "https://github.com/example/repo.git",
    "main",                       // Branch
    "abc123def456"                // Commit SHA
);
```

### Workflow ID Generation

Workflow ID is automatically generated as:
```
{appId}-{component}-{buildId}-{toolType}
```

**Example:**
- appId: `app-123`
- component: `api-component`
- buildId: `build-456`
- toolType: `GITLEAKS_SECRETS`
- **Workflow ID**: `app-123-api-component-build-456-gitleaks-secrets`


## Queue Routing by Tool Type

Each scan type (tool type) is automatically routed to its dedicated task queue:

| Tool Type | Task Queue |
|-----------|------------|
| `GITLEAKS_SECRETS` | `SECURITY_SCAN_TASK_QUEUE_GITLEAKS` |
| `GITLEAKS_FILE_HASH` | `SECURITY_SCAN_TASK_QUEUE_GITLEAKS` |
| `BLACKDUCK_DETECT` | `SECURITY_SCAN_TASK_QUEUE_BLACKDUCK` |

### Routing Priority

1. **Explicit queue** (if `config.setTaskQueue()` is called)
2. **Tool type-based queue** (automatic based on scan type)
3. **Default queue** (fallback only)

## Worker Configuration

### One Worker Per Scan Type (Recommended)

Each scan type has its own dedicated worker deployment:

**Gitleaks Worker:**
```yaml
env:
- name: SCAN_TYPE
  value: "GITLEAKS_SECRETS"  # Or GITLEAKS_FILE_HASH
```
→ Polls `SECURITY_SCAN_TASK_QUEUE_GITLEAKS`

**BlackDuck Worker:**
```yaml
env:
- name: SCAN_TYPE
  value: "BLACKDUCK_DETECT"
```
→ Polls `SECURITY_SCAN_TASK_QUEUE_BLACKDUCK`

### Benefits

- **Isolation**: Each scan type has dedicated workers
- **Scaling**: Scale workers independently per scan type
- **Resource Allocation**: Different resource limits per scan type
- **Fault Isolation**: Issues with one scan type don't affect others

## Workflow Execution

Each workflow execution handles **one scan type** (tool type):

1. **Clone Repository**: Once per workflow
2. **Execute Single Scan**: Based on toolType
3. **Store Results**: Single scan result

### Example Flow

```
Request: app-123, api-component, build-456, GITLEAKS_SECRETS
  ↓
Workflow ID: app-123-api-component-build-456-gitleaks-secrets
  ↓
Queue: SECURITY_SCAN_TASK_QUEUE_GITLEAKS
  ↓
Gitleaks Worker picks up task
  ↓
Execute: Clone → Gitleaks Scan → Store Results
```

## Multiple Scans for Same Build

If you need multiple scan types for the same build, submit **separate requests**:

```java
// Request 1: Gitleaks scan
ScanRequest gitleaksRequest = new ScanRequest(
    "app-123", "api-component", "build-456",
    ScanType.GITLEAKS_SECRETS, repoUrl, branch, commitSha
);

// Request 2: BlackDuck scan
ScanRequest blackduckRequest = new ScanRequest(
    "app-123", "api-component", "build-456",
    ScanType.BLACKDUCK_DETECT, repoUrl, branch, commitSha
);

// Both workflows:
// - Use same appId, component, buildId
// - Different toolType → different workflow IDs
// - Different queues → different workers
// - Can run in parallel
```

## Workspace Path

Workspace path is automatically generated:
```
/workspace/security-scans/{appId}-{component}-{buildId}-{toolType}
```

**Example:**
```
/workspace/security-scans/app-123-api-component-build-456-gitleaks-secrets
```

## Scan Summary

The `ScanSummary` includes all structure fields:

```java
summary.getAppId();        // "app-123"
summary.getComponent();    // "api-component"
summary.getBuildId();     // "build-456"
summary.getToolType();    // GITLEAKS_SECRETS
```

## Usage Examples

### Example 1: Submit Gitleaks Scan

```java
ScanRequest request = new ScanRequest(
    "app-123",
    "api-component",
    "build-456",
    ScanType.GITLEAKS_SECRETS,
    "https://github.com/example/repo.git",
    "main",
    "abc123def456"
);

ScanConfig config = new ScanConfig();
// Configure scan settings...
request.setScanConfig(config);

SecurityScanWorkflow workflow = client.newWorkflowStub(...);
ScanSummary summary = workflow.executeScans(request);
```

**Result:**
- Workflow ID: `app-123-api-component-build-456-gitleaks-secrets`
- Queue: `SECURITY_SCAN_TASK_QUEUE_GITLEAKS`
- Worker: Gitleaks worker picks up and executes

### Example 2: Submit BlackDuck Scan

```java
ScanRequest request = new ScanRequest(
    "app-123",
    "api-component",
    "build-456",
    ScanType.BLACKDUCK_DETECT,
    "https://github.com/example/repo.git",
    "main",
    "abc123def456"
);
```

**Result:**
- Workflow ID: `app-123-api-component-build-456-blackduck-detect`
- Queue: `SECURITY_SCAN_TASK_QUEUE_BLACKDUCK`
- Worker: BlackDuck worker picks up and executes

### Example 3: Multiple Scans for Same Build (Parallel)

```java
// Both can run in parallel - different workflows, different queues
submitScan("app-123", "api", "build-456", ScanType.GITLEAKS_SECRETS);
submitScan("app-123", "api", "build-456", ScanType.BLACKDUCK_DETECT);
```


## Key Benefits

1. **Clear Workflow Identification**: Workflow ID includes all context (app, component, build, tool)
2. **Dedicated Workers**: One worker per scan type for better isolation
3. **Parallel Execution**: Multiple scan types for same build can run in parallel
4. **Better Tracking**: Easy to identify scans by application, component, and build
5. **Scalability**: Scale workers independently per scan type
