# Scan Execution Model

## Overview

Currently, each workflow execution handles a single BlackDuck Detect scan. The structure is designed to support additional scan types in the future, which could then be executed in parallel.

## Current Execution Model

### Single Scan Execution
```
Time →
├─ Clone Repo
└─ BlackDuck Detect Scan
```

**Characteristics:**
- Space-efficient: Uses single repository clone
- One scan type per workflow execution
- Simple and predictable execution model
- Structure supports adding additional scan types in the future

## Implementation Details

### Current Implementation

Each workflow execution performs a single scan:

```java
// Execute single scan
ScanResult scanResult = executeSingleScan(toolType, repoPath, request);
summary.addScanResult(scanResult);
```

### Key Points

1. **Single Repository Clone**: One clone per workflow execution
2. **Single Scan Type**: Each workflow handles one scan type (BlackDuck Detect)
3. **Space Efficient**: Minimal space usage with single scan
4. **Future Extensibility**: Structure supports adding parallel execution when multiple scan types are added

## Configuration

### Current Configuration

No parallel execution configuration is needed since only one scan type is supported per workflow:

```java
ScanConfig config = new ScanConfig();
// Single scan execution - no parallel configuration needed
```

## Space Considerations

### Current Space Usage
```
/workspace/security-scans/{scan-id}/
├── repo/                    (single clone)
└── blackduck-output/        (created by BlackDuck scan)
```

**Space Requirements:**
- Repository clone: Varies by repository size
- BlackDuck output: ~100-500MB
- Total: Repository size + ~500MB

## Performance

### Current Execution Time
- BlackDuck Detect: Typically 10-30 minutes (varies by repository size)

**Total Time**: Single scan execution time

## Future: Parallel Execution

When additional scan types are added in the future, parallel execution may be beneficial:

### ✅ Consider Parallel When:
- Multiple scan types are needed for the same build
- Speed is critical
- Sufficient CPU/memory available
- Scanning tools are confirmed to work concurrently

### ❌ Use Sequential When:
- Limited CPU/memory resources
- Scanning tools have file locking issues
- Need to minimize resource usage

## Tool-Specific Considerations

### BlackDuck Detect
- Creates temporary files and caches during scan
- Output directory: `blackduck-output/`
- Single instance per workflow execution

## Recommendations

1. **Current Model**: Single scan per workflow is simple and predictable
2. **Future Planning**: Structure supports adding parallel execution when multiple scan types are added
3. **Monitor Resources**: Watch CPU/memory usage during scans
4. **Output Management**: BlackDuck output is stored in dedicated directory

## Example Configuration

```java
ScanConfig config = new ScanConfig();

// Configure BlackDuck scan settings
config.setBlackduckApiToken("token");
config.setBlackduckUrl("https://blackduck.example.com");

// Single scan execution - no parallel configuration needed
```

## Future Enhancements

When additional scan types are added:
- **Parallel Execution**: Run multiple scan types simultaneously
- **Hybrid Mode**: Run compatible scans in parallel, others sequentially
- **Dynamic Parallelism**: Adjust based on available resources
- **Output Isolation**: Automatic unique output paths per scan type

