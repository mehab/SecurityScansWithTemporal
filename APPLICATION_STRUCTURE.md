# Application Structure and Scan Request Format

## Overview

The security scanning application is structured to support multi-application, multi-component scanning with unique build identifiers. Each scan request is associated with:

- **Application ID (appId)**: Identifies the application being scanned
- **Component**: Component within the application
- **Build ID (buildId)**: Unique build identifier for the component
- **Tool Type**: Single scan type (BlackDuck Detect)

## Scan Request Structure

### Structure

```java
ScanRequest request = new ScanRequest(
    "app-123",                    // Application ID
    "api-component",              // Component name
    "build-456",                  // Build ID (unique per component build)
    ScanType.BLACKDUCK_DETECT,    // Tool type (single scan type)
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
- toolType: `BLACKDUCK_DETECT`
- **Workflow ID**: `app-123-api-component-build-456-blackduck-detect`


## Queue Routing by Tool Type

Each scan type (tool type) is automatically routed to its dedicated task queue:

| Tool Type | Task Queue |
|-----------|------------|
| `BLACKDUCK_DETECT` | `SECURITY_SCAN_TASK_QUEUE_BLACKDUCK` |

### Routing Priority

1. **Explicit queue** (if `config.setTaskQueue()` is called)
2. **Tool type-based queue** (automatic based on scan type)
3. **Default queue** (fallback only)

## Worker Configuration

### One Worker Per Scan Type (Recommended)

Each scan type has its own dedicated worker deployment:

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
Request: app-123, api-component, build-456, BLACKDUCK_DETECT
  ↓
Workflow ID: app-123-api-component-build-456-blackduck-detect
  ↓
Queue: SECURITY_SCAN_TASK_QUEUE_BLACKDUCK
  ↓
BlackDuck Worker picks up task
  ↓
Execute: Clone → BlackDuck Scan → Store Results
```

## Multiple Scans for Same Build

If you need multiple scan types for the same build, submit **separate requests**:

```java
// BlackDuck scan request
ScanRequest blackduckRequest = new ScanRequest(
    "app-123", "api-component", "build-456",
    ScanType.BLACKDUCK_DETECT, repoUrl, branch, commitSha
);

// Workflow:
// - Uses appId, component, buildId
// - Single toolType → single workflow ID
// - Routes to BlackDuck queue → BlackDuck workers
```

## Workspace Path

Workspace path is automatically generated:
```
/workspace/security-scans/{appId}-{component}-{buildId}-{toolType}
```

**Example:**
```
/workspace/security-scans/app-123-api-component-build-456-blackduck-detect
```

## Scan Summary

The `ScanSummary` includes all structure fields:

```java
summary.getAppId();        // "app-123"
summary.getComponent();    // "api-component"
summary.getBuildId();     // "build-456"
summary.getToolType();    // BLACKDUCK_DETECT
```

## Usage Examples

### Example 1: Submit BlackDuck Scan

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

### Example 2: Multiple Scans for Same Build (Future)

When additional scan types are added in the future, multiple scans for the same build can run in parallel:
```java
// Future: When additional scan types are supported
submitScan("app-123", "api", "build-456", ScanType.BLACKDUCK_DETECT);
// Additional scan types can be added here
```


## Key Benefits

1. **Clear Workflow Identification**: Workflow ID includes all context (app, component, build, tool)
2. **Dedicated Workers**: One worker per scan type for better isolation
3. **Better Tracking**: Easy to identify scans by application, component, and build
4. **Scalability**: Scale workers independently per scan type
5. **Future Extensibility**: Structure supports adding additional scan types in the future
