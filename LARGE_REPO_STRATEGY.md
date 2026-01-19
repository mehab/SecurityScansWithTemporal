# Large Repository Space Management

## Overview

This document explains how the system handles large repositories (4GB+) efficiently on limited PVC storage, including space-saving strategies and performance considerations.

## Problem Statement

When scanning a 4GB repository with limited PVC storage (e.g., 10GB), we need to:
1. Clone the repository efficiently
2. Execute all required scans
3. Minimize performance impact
4. Stay within storage limits

## Space Breakdown

### Typical Space Usage for 4GB Repository

```
Full Clone:          4GB (repo) + 1-2GB (git history) = ~6GB
Shallow Clone:       4GB (repo) + 0.1GB (minimal history) = ~4.1GB
Sparse Checkout:     1-2GB (only needed paths) + 0.1GB = ~1-2GB
CLI Tools:           ~130MB (Gitleaks + BlackDuck JAR)
Scan Outputs:        ~100-500MB (reports, logs)
Temporary Files:     ~200MB
Total (Shallow):     ~4.5GB
Total (Sparse):      ~2GB
```

### Understanding Git Clone Space Usage

When we say a repository is "4GB", we mean the **size of actual files**. A full clone includes:
- **Repository Files**: 4GB (current state of files)
- **Git History**: 1-2GB (all commits, branches, tags, object database)

**Full Clone**: ~6GB total (4GB files + 2GB history)
**Shallow Clone**: ~4.1GB total (4GB files + 0.1GB minimal history)
**Savings**: ~2GB by not downloading git history

## Strategies Implemented

### 1. Shallow Clone (Default for Large Repos)

**What it does:**
- Only clones the latest commit(s) instead of full history
- Reduces repository size by 50-90% for repos with long history

**Command:**
```bash
git clone --depth 1 <repo-url>
```

**Space Savings:**
- Full clone of 4GB repo: ~6GB total (4GB files + 2GB git history)
- Shallow clone: ~4.1GB total (4GB files + 0.1GB minimal history)
- **Saves ~2GB** by not downloading git history

**Configuration:**
```java
ScanConfig config = new ScanConfig();
config.setCloneStrategy(CloneStrategy.SHALLOW);
config.setShallowCloneDepth(1); // Only latest commit
```

### 2. Single Branch Clone

**What it does:**
- Only clones the specified branch, not all branches
- Reduces size when repo has many branches

**Command:**
```bash
git clone --single-branch --branch main <repo-url>
```

**Space Savings:**
- Multi-branch repo → saves 20-40% by excluding other branches

**Configuration:**
```java
config.setCloneStrategy(CloneStrategy.SHALLOW_SINGLE_BRANCH);
```

### 3. Sparse Checkout

**What it does:**
- Only checks out specific directories/paths
- Repository is cloned but only selected paths are in working directory

**Use Cases:**
- Large monorepos where only certain modules need scanning
- Repos with large binary assets that don't need scanning

**Command:**
```bash
git config core.sparseCheckout true
echo "src/" >> .git/info/sparse-checkout
echo "config/" >> .git/info/sparse-checkout
git read-tree -mu HEAD
```

**Space Savings:**
- 4GB repo with 2GB of docs/assets → ~2GB (saves 50%)

**Configuration:**
```java
config.setUseSparseCheckout(true);
config.addSparseCheckoutPath("src/");
config.addSparseCheckoutPath("config/");
config.addSparseCheckoutPath("*.java"); // Java files only
```

### 4. Git History Cleanup

**What it does:**
- Runs `git gc --aggressive` after shallow clone
- Removes unnecessary objects and packs

**Space Savings:**
- Additional 5-10% reduction

**Automatic:** Enabled for shallow clones

### 5. Incremental Cleanup

**What it does:**
- Clean up scan artifacts between scans
- Keep repository, remove temporary files

**Configuration:**
```java
config.setCleanupAfterEachScan(true);
```

## Recommended Configuration for 4GB Repository

### Option 1: Maximum Space Savings (Recommended)

```java
ScanConfig config = new ScanConfig();

// Shallow clone of single branch
config.setCloneStrategy(CloneStrategy.SHALLOW_SINGLE_BRANCH);
config.setShallowCloneDepth(1);

// Sparse checkout (if you know which paths to scan)
config.setUseSparseCheckout(true);
config.addSparseCheckoutPath("src/");
config.addSparseCheckoutPath("*.java");
config.addSparseCheckoutPath("*.js");
config.addSparseCheckoutPath("*.py");

// Cleanup between scans
config.setCleanupAfterEachScan(true);

// Sequential execution (uses less memory)
config.setExecuteScansInParallel(false);
```

**Expected Space Usage:** ~1-2GB (75% reduction)

### Option 2: Balanced (Good Performance + Space)

```java
ScanConfig config = new ScanConfig();

// Shallow clone
config.setCloneStrategy(CloneStrategy.SHALLOW);
config.setShallowCloneDepth(1);

// No sparse checkout (scan everything)
config.setUseSparseCheckout(false);

// Cleanup after all scans
config.setCleanupAfterEachScan(true);

// Parallel execution for speed
config.setExecuteScansInParallel(true);
```

**Expected Space Usage:** ~4.5GB

### Option 3: Maximum Performance

```java
ScanConfig config = new ScanConfig();

// Full clone (if you have space)
config.setCloneStrategy(CloneStrategy.FULL);

// Parallel execution
config.setExecuteScansInParallel(true);

// No cleanup until end
config.setCleanupAfterEachScan(false);
```

**Expected Space Usage:** ~6GB

## Workflow Execution Flow

```
1. Check Available Space
   └─> Must have at least 25% of max workspace size

2. Clone Repository (with space-efficient strategy)
   ├─> Shallow clone (--depth 1)
   ├─> Single branch (--single-branch)
   └─> Sparse checkout (if configured)
   
3. Cleanup Git History
   └─> git gc --aggressive

4. Execute Scans
   ├─> Sequential or Parallel
   └─> Cleanup artifacts between scans (if configured)

5. Final Cleanup
   └─> Remove entire workspace
```

## Performance Impact

### Clone Time Comparison

| Strategy | Clone Time (4GB repo) | Space Used |
|----------|----------------------|------------|
| Full Clone | 10-15 min | ~6GB |
| Shallow Clone | 3-5 min | ~4.1GB |
| Shallow + Sparse | 2-4 min | ~1-2GB |

### Scan Performance

- **Shallow Clone**: No impact on scan performance (scans work on working directory)
- **Sparse Checkout**: Faster scans (fewer files to process)
- **Parallel Execution**: Faster overall, but uses more CPU/memory

## Monitoring and Alerts

### Space Monitoring

The workflow monitors space at key points:
1. Before cloning
2. After cloning (reports repository size)
3. Before each scan

### Recommended Alerts

Set up monitoring alerts for:
- PVC storage > 80% capacity
- Workspace size > max allowed
- Clone failures due to space

## Best Practices

### 1. Know Your Repository

```java
// If repo has large binary files in specific directories
config.addSparseCheckoutPath("src/");  // Include
// Exclude: docs/, assets/, node_modules/
```

### 2. Use Shallow Clone by Default

```java
// Always use shallow for large repos
config.setCloneStrategy(CloneStrategy.SHALLOW);
config.setShallowCloneDepth(1);
```

### 3. Configure Sparse Checkout When Possible

```java
// Only checkout what you need to scan
if (repoSize > 2GB) {
    config.setUseSparseCheckout(true);
    // Add paths based on scan requirements
}
```

### 4. Monitor and Adjust

- Start with shallow clone
- Monitor actual space usage
- Adjust sparse checkout paths based on results
- Use parallel execution only if space allows

## Example: 4GB Repository Scan

### Scenario
- Repository: 4GB
- PVC Limit: 10GB
- Scans: Gitleaks Secrets, Gitleaks File Hash, BlackDuck Detect

### Configuration
```java
ScanRequest request = new ScanRequest("scan-123", repoUrl, "main", null);
request.addScanType(ScanType.GITLEAKS_SECRETS);
request.addScanType(ScanType.GITLEAKS_FILE_HASH);
request.addScanType(ScanType.BLACKDUCK_DETECT);

ScanConfig config = new ScanConfig();
config.setCloneStrategy(CloneStrategy.SHALLOW_SINGLE_BRANCH);
config.setShallowCloneDepth(1);
config.setUseSparseCheckout(true);
config.addSparseCheckoutPath("src/");
config.addSparseCheckoutPath("*.java");
config.addSparseCheckoutPath("*.js");
config.addSparseCheckoutPath("*.py");
config.setCleanupAfterEachScan(true);
config.setExecuteScansInParallel(false); // Sequential for space

request.setScanConfig(config);
```

### Expected Results
- **Clone Size**: ~1.5GB (sparse checkout of source files)
- **Scan Outputs**: ~200MB
- **Total Usage**: ~1.7GB
- **Available Space**: 8.3GB remaining
- **Performance**: Sequential scans complete in ~15-20 minutes

## Troubleshooting

### Issue: Still Running Out of Space

**Solutions:**
1. Enable sparse checkout with more specific paths
2. Reduce shallow clone depth to 1
3. Clean up between each scan
4. Increase PVC size if possible

### Issue: Scans Missing Files

**Cause:** Sparse checkout paths too restrictive

**Solution:** Review and expand sparse checkout paths

### Issue: Slow Clone Performance

**Solutions:**
1. Use shallow clone (faster)
2. Clone from closer mirror/CDN
3. Use compression: `git config core.compression 0`

## Future Enhancements

1. **Incremental Scanning**: Only scan changed files
2. **Streaming Clone**: Clone and scan in parallel
3. **Compression**: Compress repository after clone
4. **Distributed Scanning**: Split repo across multiple workers
5. **Smart Sparse Checkout**: Auto-detect paths to scan

