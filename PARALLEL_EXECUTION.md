# Parallel Scan Execution

## Overview

Yes, all scan activities can be executed in parallel! The workflow now supports both **sequential** (default) and **parallel** execution modes.

## How It Works

### Sequential Execution (Default)
```
Time →
├─ Clone Repo
├─ Gitleaks Secrets Scan ──────┐
├─ Gitleaks File Hash Scan ────┤ Sequential
└─ BlackDuck Detect Scan ──────┘
```

**Characteristics:**
- Space-efficient: Uses single repository clone
- Lower resource usage
- Total time = sum of all scan times
- Default mode

### Parallel Execution
```
Time →
├─ Clone Repo
├─ Gitleaks Secrets Scan ──────┐
├─ Gitleaks File Hash Scan ────┤ Parallel (simultaneous)
└─ BlackDuck Detect Scan ──────┘
```

**Characteristics:**
- Faster: All scans run simultaneously
- Same space usage (single repo clone)
- Total time ≈ longest scan time
- Higher CPU/memory usage

## Implementation Details

### Using Temporal's Async API

The parallel execution uses Temporal's `Async.function()` and `Promise` API:

```java
// Start all scans asynchronously
Map<ScanType, Promise<ScanResult>> promises = new HashMap<>();

for (ScanType scanType : scanTypes) {
    Promise<ScanResult> promise = Async.function(() -> {
        return executeSingleScan(scanType, repoPath, config);
    });
    promises.put(scanType, promise);
}

// Wait for all to complete
List<ScanResult> results = new ArrayList<>();
for (ScanType scanType : scanTypes) {
    Promise<ScanResult> promise = promises.get(scanType);
    ScanResult result = promise.get(); // Blocks until complete
    results.add(result);
}
```

### Key Points

1. **Same Repository**: All parallel scans operate on the same cloned repository
2. **No Additional Space**: Parallel execution doesn't require multiple clones
3. **Tool Compatibility**: Ensure scanning tools support concurrent execution:
   - **Gitleaks**: ✅ Safe to run multiple instances on same repo (read-only)
   - **BlackDuck Detect**: ⚠️ May create temporary files - test for conflicts
4. **Resource Usage**: Parallel mode uses more CPU/memory but same disk space

## Configuration

### Enable Parallel Execution

```java
ScanConfig config = new ScanConfig();
config.setExecuteScansInParallel(true);  // Enable parallel execution
```

### Default (Sequential)

```java
ScanConfig config = new ScanConfig();
// executeScansInParallel defaults to false
```

## Space Considerations

### Parallel Execution Space Usage
```
/workspace/security-scans/{scan-id}/
├── repo/                    (single clone - shared by all scans)
├── gitleaks-report.json     (created by secrets scan)
├── gitleaks-report.json     (created by file hash scan - may overwrite)
└── blackduck-output/        (created by BlackDuck scan)
```

**Note**: If scans write to the same output files, you may need to:
- Use unique output paths per scan type
- Or accept that outputs may overwrite each other

### Sequential Execution Space Usage
```
/workspace/security-scans/{scan-id}/
├── repo/                    (single clone)
├── gitleaks-report.json     (created sequentially)
└── blackduck-output/        (created sequentially)
```

## Performance Comparison

### Example: 3 Scans
- Gitleaks Secrets: 5 minutes
- Gitleaks File Hash: 3 minutes  
- BlackDuck Detect: 10 minutes

**Sequential Total**: 5 + 3 + 10 = **18 minutes**

**Parallel Total**: max(5, 3, 10) = **~10 minutes** (saves 8 minutes)

## When to Use Parallel Execution

### ✅ Use Parallel When:
- Speed is critical
- Sufficient CPU/memory available
- Scanning tools are confirmed to work concurrently
- Repository is read-only during scans

### ❌ Use Sequential When:
- Limited CPU/memory resources
- Scanning tools have file locking issues
- Need to minimize resource usage
- Space is extremely constrained (though parallel doesn't use more space)

## Tool-Specific Considerations

### Gitleaks
- ✅ **Safe for parallel**: Read-only operations
- Multiple instances can scan the same repo simultaneously
- Output files may conflict - use unique report paths

### BlackDuck Detect
- ⚠️ **Test first**: May create temporary files/caches
- Check if multiple instances conflict
- Consider using different output directories

## Recommendations

1. **Default to Sequential**: More predictable, lower resource usage
2. **Test Parallel First**: Verify tools work concurrently in your environment
3. **Monitor Resources**: Watch CPU/memory when using parallel mode
4. **Unique Output Paths**: Configure each scan to write to unique locations

## Example Configuration

```java
ScanConfig config = new ScanConfig();

// Enable parallel for faster execution
config.setExecuteScansInParallel(true);

// Still cleanup after all scans complete
config.setCleanupAfterEachScan(true);

// Configure unique output paths to avoid conflicts
config.setGitleaksConfigPath("/path/to/config.toml");
// (In activity implementation, use unique report paths per scan type)
```

## Future Enhancements

Potential improvements:
- **Hybrid Mode**: Run compatible scans in parallel, others sequentially
- **Dynamic Parallelism**: Adjust based on available resources
- **Output Isolation**: Automatic unique output paths per scan type
- **Conflict Detection**: Detect and handle tool conflicts automatically

