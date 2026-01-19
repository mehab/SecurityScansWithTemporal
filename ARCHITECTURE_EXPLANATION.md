# Security Scan Application Architecture

## Overview

This document explains how the security scanning application works with Temporal workflows and activities, designed for OpenShift/Kubernetes deployment.

## Architecture Components

### 1. Temporal Service (Orchestration Layer)

**Location**: Separate Kubernetes cluster or dedicated service

**Purpose**: 
- Manages workflow state and orchestration
- Stores workflow history
- Distributes tasks to workers
- Handles retries and timeouts

**Key Characteristics**:
- Stateless (can scale horizontally)
- Does NOT execute activities
- Only manages workflow logic and task distribution

### 2. Worker Pods (Execution Layer)

**Location**: OpenShift/Kubernetes pods in worker cluster(s)

**Components**:
- `SecurityScanWorker`: Main worker process that polls Temporal task queue
- Activity Implementations: Execute actual work (cloning, scanning, etc.)

**How It Works**:

```
┌─────────────────────────────────────────────────────────────┐
│  SecurityScanWorker Pod (OpenShift/Kubernetes)              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  SecurityScanWorker.main()                           │   │
│  │  - Connects to Temporal Service                      │   │
│  │  - Creates WorkerFactory                            │   │
│  │  - Registers Workflow & Activity implementations     │   │
│  │  - Starts polling task queue                         │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
│  Registered Components:                                       │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Workflow: SecurityScanWorkflowImpl                   │   │
│  │  Activities:                                         │   │
│  │    - RepositoryActivityImpl                          │   │
│  │    - GitleaksScanActivityImpl                        │   │
│  │    - BlackDuckScanActivityImpl                        │   │
│  │    - StorageActivityImpl                              │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 3. Shared Storage (PVC)

**Location**: OpenShift/Kubernetes PersistentVolumeClaim (ReadWriteMany - RWX)

**Purpose**:
- Stores cloned repositories
- Stores scan outputs and reports
- Shared across all worker pods
- Persists across pod failures

**Structure**:
```
/workspace/security-scans/
  ├── scan-1234567890/
  │   ├── repo/              (cloned repository)
  │   ├── gitleaks-report.json
  │   └── blackduck-output/
  └── scan-9876543210/
      └── repo/
```

## Execution Flow

### Step 1: Client Initiates Scan

```java
// SecurityScanClient.java
WorkflowClient client = WorkflowClient.newInstance(serviceStub);
SecurityScanWorkflow workflow = client.newWorkflowStub(
    SecurityScanWorkflow.class, 
    options
);
ScanSummary summary = workflow.executeScans(scanRequest);
```

**What Happens**:
1. Client connects to Temporal Service
2. Creates workflow stub (proxy, not actual execution)
3. Calls `executeScans()` - this is a **signal** to Temporal Service
4. Temporal Service creates workflow execution and queues it
5. Client can wait for result or disconnect (workflow continues)

### Step 2: Temporal Service Distributes Work

**Temporal Service**:
1. Receives workflow execution request
2. Creates workflow instance (in-memory or persisted)
3. Executes workflow code until it hits an activity call
4. When workflow calls an activity, Temporal:
   - Suspends workflow execution
   - Creates an **Activity Task**
   - Puts task in task queue (`SECURITY_SCAN_TASK_QUEUE`)
   - Waits for worker to complete activity

### Step 3: Worker Picks Up Task

**SecurityScanWorker** (running in OpenShift/Kubernetes pod):
1. Continuously polls task queue: `SECURITY_SCAN_TASK_QUEUE`
2. Receives activity task from Temporal Service
3. Looks up registered activity implementation
4. Executes activity method
5. Returns result to Temporal Service
6. Temporal Service resumes workflow execution

**Example Flow**:

```
Temporal Service                    Worker Pod
     │                                   │
     │ 1. Workflow calls                 │
     │    cloneRepository()              │
     │──────────────────────────────────>│
     │                                   │
     │ 2. Creates Activity Task          │
     │    in task queue                  │
     │                                   │
     │ 3. Worker polls queue             │
     │    <──────────────────────────────│
     │                                   │
     │ 4. Worker executes                │
     │    RepositoryActivityImpl         │
     │    .cloneRepository()             │
     │    (clones repo to PVC)           │
     │                                   │
     │ 5. Worker returns result          │
     │    <──────────────────────────────│
     │                                   │
     │ 6. Workflow resumes               │
     │    with repo path                │
     │                                   │
```

### Step 4: Workflow Orchestration

**SecurityScanWorkflowImpl** orchestrates the entire process:

```java
public ScanSummary executeScans(ScanRequest request) {
    // Step 0: Update tools (if needed)
    updateGitleaksIfNeeded(config);
    
    // Step 1: Clone repository
    repoPath = repositoryActivity.cloneRepository(request);
    // ↑ This is an activity call - workflow suspends here
    
    // Step 2: Execute scans
    if (executeInParallel) {
        scanResults = executeScansInParallel(...);
        // ↑ Multiple activity calls in parallel
    } else {
        scanResults = executeScansSequentially(...);
        // ↑ Activity calls one after another
    }
    
    // Step 3: Store results
    storageActivity.storeScanResults(...);
    // ↑ Another activity call
    
    // Step 4: Cleanup (if successful)
    if (allSuccessful) {
        repositoryActivity.cleanupWorkspace(...);
    }
    
    return summary;
}
```

**Key Points**:
- Each activity call **suspends** the workflow
- Workflow code is **deterministic** (no random, time, I/O)
- Activities run on **worker pods** (can be different pods)
- Activities can be **retried** automatically by Temporal

### Step 5: Activity Execution

**Example: RepositoryActivityImpl.cloneRepository()**

```java
public String cloneRepository(ScanRequest request) {
    ActivityExecutionContext context = Activity.getExecutionContext();
    
    // Send heartbeat (keeps activity alive)
    context.heartbeat("Starting clone...");
    
    // Check space
    long availableSpace = getAvailableSpace(workspacePath);
    
    // Clone repository
    ProcessBuilder pb = new ProcessBuilder("git", "clone", ...);
    Process process = pb.start();
    
    // Send heartbeat during long operation
    context.heartbeat("Cloning in progress...");
    
    process.waitFor();
    
    return repoPath;
}
```

**What Happens**:
1. Activity runs on **worker pod** (not Temporal Service)
2. Can access **PVC storage** (mounted at `/workspace`)
3. Can execute **external commands** (git, gitleaks, etc.)
4. Sends **heartbeats** to Temporal Service (keeps activity alive)
5. Returns result to Temporal Service
6. Temporal Service **resumes workflow** with result

## Parallel Execution

### How Parallel Scans Work

```java
// In SecurityScanWorkflowImpl
private List<ScanResult> executeScansInParallel(...) {
    Map<ScanType, Promise<ScanResult>> promises = new HashMap<>();
    
    // Start all scans asynchronously
    for (ScanType scanType : scanTypes) {
        Promise<ScanResult> promise = Async.function(() -> 
            executeSingleScanWithErrorHandling(scanType, repoPath, config)
        );
        promises.put(scanType, promise);
    }
    
    // Wait for all to complete
    List<ScanResult> results = new ArrayList<>();
    for (ScanType scanType : scanTypes) {
        results.add(promises.get(scanType).get()); // Blocks until complete
    }
    
    return results;
}
```

**What Happens**:
1. Workflow creates multiple **activity tasks** simultaneously
2. Temporal Service distributes tasks to **available workers**
3. Multiple worker pods can execute activities **in parallel**
4. All activities read from **same repository** on shared PVC
5. Workflow waits for **all activities** to complete
6. Results are collected and returned

**Key Point**: Parallel execution uses **same repository clone** (no additional space), but activities run on **different worker pods** (if available).

## Fault Tolerance

### Pod Failure During Activity

**Scenario**: Worker pod crashes while executing `gitleaksActivity.scanSecrets()`

**What Happens**:
1. Temporal Service detects missing heartbeat
2. Marks activity as failed
3. **Retries activity** on different worker pod
4. New worker pod accesses **same repository** on shared PVC
5. Activity continues from retry (idempotent design)
6. Workflow eventually completes

**Key Point**: Repository on **shared PVC** persists across pod failures.

### Storage Failure

**Scenario**: Persistent volume (PVC) fails or becomes unavailable

**What Happens**:
1. Activities detect storage failure via `StorageHealthChecker`
2. `StorageFailureException` thrown
3. Workflow catches exception and marks for restart
4. Workflow fails immediately
5. External system restarts workflow when storage restored

**Key Point**: Workflows must restart from beginning when storage fails.

See [FAILURE_HANDLING.md](FAILURE_HANDLING.md) for comprehensive failure handling details.

## Space Management

### Space Check Flow

```java
// In RepositoryActivityImpl.cloneRepository()
long availableSpace = getAvailableSpace(workspacePath);

// Calculate required space:
long estimatedRepoSize = estimateRepositorySize(request, config);
long cliToolsSize = Shared.TOTAL_CLI_TOOLS_SIZE; // ~180MB
long scanOutputsSize = 500L * 1024 * 1024; // ~500MB
long tempFilesSize = 200L * 1024 * 1024; // ~200MB
long minRequiredSpace = estimatedRepoSize + cliToolsSize + 
                        scanOutputsSize + tempFilesSize;

if (availableSpace < minRequiredSpace) {
    throw new InsufficientSpaceException(...);
    // Temporal will retry later (space may become available)
}
```

**Space Calculation Includes**:
- Repository clone size (estimated)
- CLI tools (Gitleaks + BlackDuck Detect) - ~180MB
- Scan outputs (reports, logs) - ~500MB
- Temporary files - ~200MB
- Safety buffer (25% of max workspace size)

### Cleanup Strategy

**When Cleanup Happens**:
1. **All scans successful** → Cleanup immediately
2. **Any scan fails** → Cleanup deferred (repository retained)
3. **Workflow exception** → Cleanup in error handler

**Cleanup Process**:
```java
repositoryActivity.cleanupWorkspace(workspacePath);
// Deletes: /workspace/security-scans/{scan-id}/
```

## Deployment Architecture

### OpenShift/Kubernetes Deployment

```
┌─────────────────────────────────────────────────────────────┐
│  Temporal Service Cluster (Separate OpenShift/K8s)          │
│  - Temporal Frontend                                         │
│  - Temporal History                                          │
│  - Temporal Matching                                         │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ gRPC
                            │
┌─────────────────────────────────────────────────────────────┐
│  Worker OpenShift/Kubernetes Cluster                         │
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Worker Pod 1 │  │ Worker Pod 2 │  │ Worker Pod 3 │     │
│  │              │  │              │  │              │     │
│  │ SecurityScan │  │ SecurityScan │  │ SecurityScan │     │
│  │ Worker       │  │ Worker       │  │ Worker       │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                 │                 │              │
│         └─────────────────┼─────────────────┘             │
│                           │                                │
│                    ┌──────▼──────┐                         │
│                    │  PVC (RWX)  │                         │
│                    │  /workspace │                         │
│                    └─────────────┘                         │
└─────────────────────────────────────────────────────────────┘
```

### OpenShift Deployment

OpenShift is Kubernetes-based, so the architecture is identical to Kubernetes deployment. Key OpenShift-specific considerations:

```
┌─────────────────────────────────────────────────────────────┐
│  Temporal Service (Separate OpenShift Cluster)                │
│  - Temporal Frontend                                         │
│  - Temporal History                                          │
│  - Temporal Matching                                         │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ gRPC
                            │
┌─────────────────────────────────────────────────────────────┐
│  Worker OpenShift Cluster                                    │
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Worker Pod 1 │  │ Worker Pod 2 │  │ Worker Pod 3 │     │
│  │              │  │              │  │              │     │
│  │ SecurityScan │  │ SecurityScan │  │ SecurityScan │     │
│  │ Worker       │  │ Worker       │  │ Worker       │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                 │                 │              │
│         └─────────────────┼─────────────────┘             │
│                           │                                │
│                    ┌──────▼──────┐                         │
│                    │  PVC (RWX)  │                         │
│                    │  /workspace │                         │
│                    └─────────────┘                         │
└─────────────────────────────────────────────────────────────┘
```

**OpenShift-Specific Considerations**:

1. **Storage Classes**: OpenShift provides built-in storage classes:
   - Use `ReadWriteMany` (RWX) access mode for shared storage
   - Common storage classes: `gp3`, `nfs`, or custom NFS provisioner
   - Ensure storage class supports RWX for parallel execution

2. **Security Context**: OpenShift uses Security Context Constraints (SCCs):
   - Worker pods may need specific SCC permissions
   - May need to run as non-root user (OpenShift default)
   - Ensure pods can execute git, gitleaks, and other CLI tools

3. **Service Accounts**: 
   - Create dedicated ServiceAccount for workers
   - Grant necessary permissions for PVC access
   - May need RBAC rules for storage operations

4. **Network Policies**:
   - Ensure worker pods can reach Temporal Service
   - May need to configure network policies for cross-cluster communication

5. **Resource Quotas**:
   - Set appropriate resource quotas for namespace
   - Account for PVC storage limits
   - Monitor CPU/memory usage for worker pods

6. **Deployment Configuration**:
   - Use OpenShift DeploymentConfig or standard Kubernetes Deployment
   - Configure health checks (liveness/readiness probes)
   - Set up horizontal pod autoscaling if needed

## Task Queue and Load Distribution

### How Tasks Are Distributed

1. **Temporal Service** maintains task queue: `SECURITY_SCAN_TASK_QUEUE`
2. **Multiple worker pods** poll the same queue
3. Temporal uses **round-robin** or **sticky** distribution
4. When activity is called, Temporal:
   - Picks available worker
   - Sends activity task
   - Worker executes and returns result

**Benefits**:
- **Horizontal scaling**: Add more worker pods to handle more load
- **Load balancing**: Temporal distributes tasks across workers
- **Fault tolerance**: If worker fails, task goes to another worker

## Summary

### Key Concepts

1. **Temporal Service**: Orchestrates workflows, manages state, distributes tasks
2. **Worker Pods**: Execute activities (actual work happens here)
3. **Shared Storage (PVC)**: Persists repositories across pod failures (RWX access mode)
4. **Task Queue**: Communication channel between Temporal Service and workers
5. **Activities**: Actual work (cloning, scanning) - can be retried
6. **Workflows**: Orchestration logic - deterministic, can't be retried

### Execution Model

- **Workflow code** runs on Temporal Service (orchestration)
- **Activity code** runs on worker pods (execution)
- **Storage** is shared across all workers (PVC with RWX access mode)
- **Tasks** are distributed by Temporal Service to available workers
- **Retries** happen automatically on activity failures

### Benefits

- **Separation**: Temporal Service separate from workers (can scale independently)
- **Scalability**: Add more worker pods to handle more scans
- **Fault Tolerance**: Pod failures don't lose work (retries + shared storage)
- **Space Efficiency**: Single repository clone for all scans (parallel or sequential)

