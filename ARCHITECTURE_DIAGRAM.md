# Security Scan Application - Architecture Diagram

## High-Level Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           SECURITY SCAN APPLICATION                                  │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐  │
│  │                         CLIENT LAYER                                          │  │
│  │                                                                               │  │
│  │  ┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐  │  │
│  │  │ SecurityScanClient│      │  External System │      │  Manual Trigger  │  │  │
│  │  │                  │      │  (CI/CD, API)    │      │                  │  │  │
│  │  └────────┬─────────┘      └────────┬─────────┘      └────────┬─────────┘  │  │
│  │           │                          │                          │            │  │
│  │           └──────────────────────────┼──────────────────────────┘            │  │
│  │                                      │                                         │  │
│  │                           ┌──────────▼──────────┐                             │  │
│  │                           │  ScanRequest        │                             │  │
│  │                           │  + ScanConfig       │                             │  │
│  │                           │  + Priority         │                             │  │
│  │                           │  + Timeouts         │                             │  │
│  │                           └──────────┬──────────┘                             │  │
│  └──────────────────────────────────────┼───────────────────────────────────────┘  │
│                                          │                                          │
│                          ┌───────────────▼────────────────┐                        │
│                          │  QUEUE ROUTING LOGIC           │                        │
│                          │  (determineTaskQueue())         │                        │
│                          │                                 │                        │
│                          │  1. Explicit queue? → Use it   │                        │
│                          │  2. Priority=HIGH? → Priority  │                        │
│                          │  3. Long timeout? → Long-run   │                        │
│                          │  4. Default → Default queue    │                        │
│                          └───────────────┬────────────────┘                        │
│                                          │                                          │
│              ┌───────────────────────────┼───────────────────────────┐            │
│              │                           │                           │            │
│    ┌─────────▼─────────┐    ┌───────────▼──────────┐    ┌───────────▼──────────┐ │
│    │ DEFAULT QUEUE      │    │ LONG-RUNNING QUEUE   │    │ PRIORITY QUEUE       │ │
│    │ (Normal scans)     │    │ (>30 min scans)      │    │ (HIGH priority)      │ │
│    │                    │    │                      │    │                      │ │
│    │ SECURITY_SCAN_     │    │ SECURITY_SCAN_       │    │ SECURITY_SCAN_       │ │
│    │ TASK_QUEUE         │    │ TASK_QUEUE_          │    │ TASK_QUEUE_          │ │
│    │                    │    │ LONG_RUNNING         │    │ PRIORITY             │ │
│    └─────────┬─────────┘    └───────────┬──────────┘    └───────────┬──────────┘ │
│              │                           │                           │            │
│              └───────────────────────────┼───────────────────────────┘            │
│                                          │                                          │
│                          ┌───────────────▼────────────────┐                        │
│                          │   TEMPORAL SERVICE              │                        │
│                          │   (Task Queue Management)       │                        │
│                          └───────────────┬────────────────┘                        │
│                                          │                                          │
│  ┌──────────────────────────────────────┼───────────────────────────────────────┐  │
│  │                    WORKER LAYER                                                │  │
│  │                                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │                    WORKER POLLING CONFIGURATION                          │  │  │
│  │  │                                                                           │  │  │
│  │  │  Option 1: Single Worker (Default)                                       │  │  │
│  │  │  ┌─────────────────────────────────────────────────────────────────────┐ │  │  │
│  │  │  │ SecurityScanWorker                                                  │ │  │  │
│  │  │  │ TASK_QUEUES: (not set)                                              │ │  │  │
│  │  │  │ → Polls ALL queues                                                  │ │  │  │
│  │  │  │   • Default                                                         │ │  │  │
│  │  │  │   • Long-Running                                                    │ │  │  │
│  │  │  │   • Priority                                                        │ │  │  │
│  │  │  └─────────────────────────────────────────────────────────────────────┘ │  │  │
│  │  │                                                                           │  │  │
│  │  │  Option 2: Separate Workers                                              │  │  │
│  │  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │  │  │
│  │  │  │ Worker-1     │  │ Worker-2     │  │ Worker-3     │                  │  │  │
│  │  │  │ TASK_QUEUES= │  │ TASK_QUEUES= │  │ TASK_QUEUES= │                  │  │  │
│  │  │  │ DEFAULT      │  │ LONG_RUNNING │  │ PRIORITY     │                  │  │  │
│  │  │  │              │  │              │  │              │                  │  │  │
│  │  │  │ Polls:       │  │ Polls:       │  │ Polls:       │                  │  │  │
│  │  │  │ • Default    │  │ • Long-Run   │  │ • Priority   │                  │  │  │
│  │  │  └──────────────┘  └──────────────┘  └──────────────┘                  │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │                    WORKER EXECUTION                                      │  │  │
│  │  │                                                                           │  │  │
│  │  │  Each Worker:                                                            │  │  │
│  │  │  ┌─────────────────────────────────────────────────────────────────────┐ │  │  │
│  │  │  │ 1. Polls Temporal for tasks from assigned queues                    │ │  │  │
│  │  │  │ 2. Receives workflow task → Executes SecurityScanWorkflow          │ │  │  │
│  │  │  │ 3. Workflow orchestrates activities:                                │ │  │  │
│  │  │  │    • Clone Repository (RepositoryActivity)                          │ │  │  │
│  │  │  │    • Run Scans (GitleaksScanActivity, BlackDuckScanActivity)       │ │  │  │
│  │  │  │    • Store Results (StorageActivity)                                │ │  │  │
│  │  │  │ 4. Activities execute on worker pod (can be different pods)        │ │  │  │
│  │  │  │ 5. All pods share same PVC (ReadWriteMany) for repository access   │ │  │  │
│  │  │  └─────────────────────────────────────────────────────────────────────┘ │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐  │
│  │                    STORAGE LAYER (Kubernetes PVC)                             │  │
│  │                                                                               │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  PersistentVolumeClaim (ReadWriteMany - RWX)                             │ │  │
│  │  │  /workspace/security-scans/                                              │ │  │
│  │  │                                                                          │ │  │
│  │  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐     │ │  │
│  │  │  │ scan-12345/      │  │ scan-67890/      │  │ scan-11111/      │     │ │  │
│  │  │  │ ├── repo/        │  │ ├── repo/        │  │ ├── repo/        │     │ │  │
│  │  │  │ ├── reports/     │  │ ├── reports/     │  │ ├── reports/     │     │ │  │
│  │  │  │ └── results/     │  │ └── results/     │  │ └── results/     │     │ │  │
│  │  │  └──────────────────┘  └──────────────────┘  └──────────────────┘     │ │  │
│  │  │                                                                          │ │  │
│  │  │  Shared across all worker pods for parallel execution                   │ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐  │
│  │                    FAILURE HANDLING & RESTART SERVICE                         │  │
│  │                                                                               │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  Workflow Execution States                                               │ │  │
│  │  │                                                                          │ │  │
│  │  │  ┌──────────────┐                                                        │ │  │
│  │  │  │  RUNNING     │ ────┐                                                 │ │  │
│  │  │  └──────────────┘     │                                                 │ │  │
│  │  │       │               │                                                 │ │  │
│  │  │       │ Success       │ Failure                                         │ │  │
│  │  │       │               │                                                 │ │  │
│  │  │  ┌────▼──────┐   ┌────▼──────┐                                          │ │  │
│  │  │  │ COMPLETED │   │  FAILED   │                                          │ │  │
│  │  │  └───────────┘   └────┬──────┘                                          │ │  │
│  │  │                       │                                                  │ │  │
│  │  │                       │ Failure Types:                                   │ │  │
│  │  │                       │ • StorageFailureException                       │ │  │
│  │  │                       │ • NetworkFailureException                        │ │  │
│  │  │                       │ • ResourceExhaustionException                    │ │  │
│  │  │                       │ • DeploymentFailureException                     │ │  │
│  │  │                       └────┬──────┐                                     │ │  │
│  │  │                             │      │                                     │ │  │
│  │  │                    ┌────────▼──┐  │                                     │ │  │
│  │  │                    │ Temporal  │  │                                     │ │  │
│  │  │                    │ History   │  │                                     │ │  │
│  │  │                    │ (Failed   │  │                                     │ │  │
│  │  │                    │  Workflows)│  │                                     │ │  │
│  │  │                    └───────────┘  │                                     │ │  │
│  │  └────────────────────────────────────┼─────────────────────────────────────┘ │  │
│  │                                       │                                       │  │
│  │  ┌───────────────────────────────────▼───────────────────────────────────┐ │  │
│  │  │  WorkflowRestartService (CronJob - runs every 30 minutes)              │ │  │
│  │  │                                                                         │ │  │
│  │  │  ┌───────────────────────────────────────────────────────────────────┐ │ │  │
│  │  │  │ 1. Query Temporal for Failed Workflows                            │ │ │  │
│  │  │  │    Query: "TaskQueue = X AND ExecutionStatus = 'FAILED'"          │ │ │  │
│  │  │  │                                                                   │ │ │  │
│  │  │  │ 2. Filter by Failure Type                                         │ │ │  │
│  │  │  │    • StorageFailureException                                       │ │ │  │
│  │  │  │    • NetworkFailureException                                       │ │ │  │
│  │  │  │    • ResourceExhaustionException                                   │ │ │  │
│  │  │  │    • DeploymentFailureException                                    │ │ │  │
│  │  │  │    • ALL (any restartable failure)                                 │ │ │  │
│  │  │  │                                                                   │ │ │  │
│  │  │  │ 3. Verify Conditions (e.g., storage health for storage failures)  │ │ │  │
│  │  │  │                                                                   │ │ │  │
│  │  │  │ 4. For each failed workflow:                                      │ │ │  │
│  │  │  │    a. Query workflow for original ScanRequest                      │ │ │  │
│  │  │  │    b. Determine appropriate queue (same routing logic)            │ │ │  │
│  │  │  │    c. Restart workflow with original request                       │ │ │  │
│  │  │  │                                                                   │ │ │  │
│  │  │  │ 5. New workflow execution starts from beginning                    │ │ │  │
│  │  │  │    → Routes to appropriate queue                                  │ │ │  │
│  │  │  │    → Worker picks up and executes                                 │ │ │  │
│  │  │  └───────────────────────────────────────────────────────────────────┘ │ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Detailed Flow Diagrams

### 1. Scan Submission and Queue Routing Flow

```
┌─────────────┐
│   Client    │
│  Submits    │
│   Scan      │
└──────┬──────┘
       │
       │ ScanRequest + ScanConfig
       │
       ▼
┌─────────────────────────────────┐
│  SecurityScanClient              │
│  determineTaskQueue()            │
└──────┬──────────────────────────┘
       │
       │ Queue Selection Logic:
       │
       ├─→ Explicit queue set?
       │   YES → Use that queue
       │   NO  ↓
       │
       ├─→ Priority = HIGH?
       │   YES → SECURITY_SCAN_TASK_QUEUE_PRIORITY
       │   NO  ↓
       │
       ├─→ Timeout > 30 min OR Workflow > 1 hour?
       │   YES → SECURITY_SCAN_TASK_QUEUE_LONG_RUNNING
       │   NO  ↓
       │
       └─→ SECURITY_SCAN_TASK_QUEUE_DEFAULT
       
       │
       ▼
┌─────────────────────────────────┐
│  Temporal Service                │
│  Workflow Started                │
│  Task Queued                     │
└─────────────────────────────────┘
```

### 2. Worker Task Picking Flow

```
┌─────────────────────────────────────────────────────────┐
│  Temporal Service                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ DEFAULT      │  │ LONG-RUN    │  │ PRIORITY     │   │
│  │ QUEUE        │  │ QUEUE       │  │ QUEUE        │   │
│  │              │  │              │  │              │   │
│  │ [Task 1]     │  │ [Task 5]     │  │ [Task 8]     │   │
│  │ [Task 2]     │  │ [Task 6]     │  │ [Task 9]     │   │
│  │ [Task 3]     │  │              │  │              │   │
│  │ [Task 4]     │  │              │  │              │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │
│         │                 │                 │            │
│         └─────────────────┼─────────────────┘            │
│                           │                              │
└───────────────────────────┼──────────────────────────────┘
                            │
                            │ Polling
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ Worker-1     │   │ Worker-2     │   │ Worker-3     │
│              │   │              │   │              │
│ Polls:       │   │ Polls:       │   │ Polls:        │
│ • Default    │   │ • Long-Run   │   │ • Priority    │
│ • Long-Run   │   │              │   │               │
│ • Priority   │   │              │   │               │
│              │   │              │   │               │
│ Picks:       │   │ Picks:       │   │ Picks:        │
│ Task 1       │   │ Task 5       │   │ Task 8        │
│ Task 2       │   │ Task 6       │   │ Task 9        │
└──────┬───────┘   └──────┬───────┘   └──────┬───────┘
       │                  │                  │
       │ Execute          │ Execute          │ Execute
       │ Workflow         │ Workflow         │ Workflow
       │                  │                  │
       ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────┐
│  Workflow Execution                                 │
│  • Clone Repository (shared PVC)                    │
│  • Run Scans                                        │
│  • Store Results                                    │
└─────────────────────────────────────────────────────┘
```

### 3. Failure Detection and Restart Flow

```
┌─────────────────────────────────────────────────────────┐
│  Workflow Execution                                      │
│                                                          │
│  ┌──────────────┐                                        │
│  │ Clone Repo   │ ────┐                                 │
│  └──────────────┘      │                                 │
│         │              │                                 │
│         ▼              │                                 │
│  ┌──────────────┐      │                                 │
│  │ Run Scans    │      │                                 │
│  └──────────────┘      │                                 │
│         │              │                                 │
│         │              │ Failure Occurs                  │
│         │              │                                 │
│         ▼              ▼                                 │
│  ┌──────────────────────────────────────┐              │
│  │  Exception Caught                     │              │
│  │  • StorageFailureException            │              │
│  │  • NetworkFailureException            │              │
│  │  • ResourceExhaustionException         │              │
│  │  • DeploymentFailureException         │              │
│  └──────────────┬───────────────────────┘              │
│                 │                                       │
│                 │ Workflow Fails                        │
│                 │                                       │
│                 ▼                                       │
│  ┌──────────────────────────────────────┐              │
│  │  Temporal History                     │              │
│  │  Status: FAILED                       │              │
│  │  Failure Reason: [Exception Type]     │              │
│  │  Original Request: [Stored]          │              │
│  └──────────────┬───────────────────────┘              │
└─────────────────┼───────────────────────────────────────┘
                  │
                  │ Every 30 minutes
                  │
                  ▼
┌─────────────────────────────────────────────────────────┐
│  WorkflowRestartService (CronJob)                       │
│                                                          │
│  1. Query Temporal:                                     │
│     "ExecutionStatus = 'FAILED'"                        │
│                                                          │
│  2. Filter by Failure Type:                            │
│     • StorageFailureException                           │
│     • NetworkFailureException                           │
│     • ResourceExhaustionException                       │
│     • DeploymentFailureException                        │
│     • ALL (configurable)                                │
│                                                          │
│  3. For each failed workflow:                           │
│     ┌──────────────────────────────────────┐           │
│     │ a. Query workflow.getOriginalRequest()│           │
│     │ b. Verify conditions (e.g., storage)  │           │
│     │ c. Determine queue (same routing)     │           │
│     │ d. Restart workflow                   │           │
│     └──────────────────────────────────────┘           │
│                                                          │
│  4. New workflow execution:                             │
│     ┌──────────────────────────────────────┐           │
│     │ • Routes to appropriate queue         │           │
│     │ • Worker picks up task               │           │
│     │ • Executes from beginning            │           │
│     └──────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────┘
```

### 4. Complete End-to-End Flow

```
┌──────────────┐
│  1. Client   │
│  Submits     │
│  Scan        │
└──────┬───────┘
       │
       │ ScanRequest
       │ + Priority/Timeout
       ▼
┌──────────────────┐
│  2. Queue        │
│  Routing         │
│  (Auto-detect)   │
└──────┬───────────┘
       │
       ├─→ Priority Queue
       ├─→ Long-Running Queue
       └─→ Default Queue
       
       │
       ▼
┌──────────────────┐
│  3. Temporal     │
│  Queues Task     │
└──────┬───────────┘
       │
       │ Polling
       ▼
┌──────────────────┐
│  4. Worker       │
│  Picks Task      │
│  (from queue)    │
└──────┬───────────┘
       │
       │ Execute
       ▼
┌──────────────────┐
│  5. Workflow     │
│  Execution       │
│  • Clone         │
│  • Scan          │
│  • Store         │
└──────┬───────────┘
       │
       ├─→ Success → Complete
       │
       └─→ Failure → Mark as Failed
                     │
                     │ (Every 30 min)
                     ▼
              ┌──────────────────┐
              │  6. Restart      │
              │  Service         │
              │  • Finds failed  │
              │  • Restarts      │
              └──────┬───────────┘
                     │
                     │ New workflow
                     ▼
              ┌──────────────────┐
              │  7. Back to      │
              │  Step 2          │
              │  (Queue Routing) │
              └──────────────────┘
```

## Key Components

### 1. Queue Routing
- **Location**: `SecurityScanClient.determineTaskQueue()`
- **Logic**: Priority-based → Timeout-based → Default
- **Result**: Workflow routed to appropriate queue

### 2. Worker Polling
- **Location**: `SecurityScanWorker`
- **Configuration**: `TASK_QUEUES` environment variable
- **Default**: Polls all queues
- **Custom**: Polls specified queues (comma-separated)

### 3. Task Execution
- **Workflow**: `SecurityScanWorkflowImpl`
- **Activities**: Repository, Gitleaks, BlackDuck, Storage
- **Storage**: Shared PVC (ReadWriteMany) across all pods

### 4. Failure Detection
- **Location**: `SecurityScanWorkflowImpl` exception handling
- **Types**: Storage, Network, Resource, Deployment
- **Storage**: Temporal workflow history

### 5. Restart Service
- **Location**: `WorkflowRestartService` (CronJob)
- **Schedule**: Every 30 minutes
- **Process**: Query → Filter → Verify → Restart
- **Configuration**: `FAILURE_TYPE` environment variable

## Environment Variables

### Worker Configuration
- `TASK_QUEUES`: Comma-separated list of queues to poll (default: all queues)
- `TEMPORAL_ADDRESS`: Temporal service address
- `WORKSPACE_BASE_DIR`: Workspace directory path

### Restart Service Configuration
- `TASK_QUEUE`: Queue to monitor for failures
- `FAILURE_TYPE`: Type of failures to restart (STORAGE_FAILURE, NETWORK_FAILURE, RESOURCE_EXHAUSTION, DEPLOYMENT_FAILURE, ALL)
- `VERIFY_STORAGE`: Verify storage health before restarting
- `USE_NEW_WORKFLOW_ID`: Use new workflow IDs for restarts

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Kubernetes/OpenShift Cluster                            │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Temporal Service (External or Internal)           │ │
│  └────────────────────────────────────────────────────┘ │
│                          │                               │
│                          │ gRPC                          │
│                          │                               │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Worker Pods (Deployment)                         │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐        │ │
│  │  │ Worker-1 │  │ Worker-2 │  │ Worker-3 │        │ │
│  │  │          │  │          │  │          │        │ │
│  │  │ Polls:   │  │ Polls:   │  │ Polls:   │        │ │
│  │  │ All      │  │ All      │  │ All      │        │ │
│  │  │ Queues   │  │ Queues   │  │ Queues   │        │ │
│  │  └────┬─────┘  └────┬─────┘  └────┬─────┘        │ │
│  │       │             │             │                │ │
│  │       └─────────────┼─────────────┘                │ │
│  │                     │                              │ │
│  │                     ▼                              │ │
│  │       ┌─────────────────────────┐                 │ │
│  │       │  PVC (ReadWriteMany)    │                 │ │
│  │       │  /workspace/security-   │                 │ │
│  │       │  scans/                 │                 │ │
│  │       └─────────────────────────┘                 │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Restart Service (CronJob)                         │ │
│  │  Runs every 30 minutes                             │ │
│  │  • Queries Temporal for failed workflows           │ │
│  │  • Restarts workflows when conditions restored     │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
└─────────────────────────────────────────────────────────┘
```
