# Security Scan Application - Architecture Diagram

## Application Structure

Each scan request is structured as:
- **Application ID (appId)**: Identifies the application
- **Component**: Component within the application
- **Build ID (buildId)**: Unique build identifier for the component
- **Tool Type**: Single scan type (BlackDuck, etc.)

**Workflow ID Format**: `{appId}-{component}-{buildId}-{toolType}`

Example: `app-123-api-component-build-456-blackduck-detect`

**Note**: The application structure is designed to support multiple scan types. Currently only BlackDuck Detect is implemented, but additional scan types can be added in the future.

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
│  │                           │  + appId            │                             │  │
│  │                           │  + component        │                             │  │
│  │                           │  + buildId          │                             │  │
│  │                           │  + toolType         │                             │  │
│  │                           │  + ScanConfig       │                             │  │
│  │                           └──────────┬──────────┘                             │  │
│  └──────────────────────────────────────┼───────────────────────────────────────┘  │
│                                          │                                          │
│                          ┌───────────────▼────────────────┐                        │
│                          │  QUEUE ROUTING LOGIC           │                        │
│                          │  (determineTaskQueue())         │                        │
│                          │                                 │                        │
│                          │  1. Explicit queue? → Use it   │                        │
│                          │  2. Tool Type → Scan-type queue│                        │
│                          │     • BlackDuck → BLACKDUCK   │                        │
│                          │  3. Default → Default queue    │                        │
│                          └───────────────┬────────────────┘                        │
│                                          │                                          │
│              ┌───────────────────────────┼───────────────────────────┐            │
│              │                           │                           │            │
│    ┌─────────▼─────────┐    ┌───────────▼──────────┐                │            │
│    │ BLACKDUCK QUEUE   │    │ DEFAULT QUEUE         │                │            │
│    │ (BlackDuck scans) │    │ (Fallback)            │                │            │
│    │                   │    │                      │                │            │
│    │ SECURITY_SCAN_    │    │ SECURITY_SCAN_        │                │            │
│    │ TASK_QUEUE_       │    │ TASK_QUEUE            │                │            │
│    │ BLACKDUCK         │    │                      │                │            │
│    └─────────┬─────────┘    └───────────┬──────────┘                │            │
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
│  │  │  Option 1: Scan-Type Specific Workers (Recommended)                     │  │  │
│  │  │  ┌─────────────────────────────────────────────────────────────────────┐ │  │  │
│  │  │  │ Worker for BlackDuck                                                │ │  │  │
│  │  │  │ SCAN_TYPE=BLACKDUCK_DETECT                                           │ │  │  │
│  │  │  │ → Polls SECURITY_SCAN_TASK_QUEUE_BLACKDUCK                          │ │  │  │
│  │  │  └─────────────────────────────────────────────────────────────────────┘ │  │  │
│  │  │                                                                           │  │  │
│  │  │  Option 2: Multi-Queue Worker (Fallback)                                  │  │  │
│  │  │  ┌─────────────────────────────────────────────────────────────────────┐ │  │  │
│  │  │  │ SecurityScanWorker                                                  │ │  │  │
│  │  │  │ SCAN_TYPE: (not set)                                                │ │  │  │
│  │  │  │ → Polls ALL scan-type queues                                        │ │  │  │
│  │  │  │   • BlackDuck                                                       │ │  │  │
│  │  │  │   • Default (fallback)                                             │ │  │  │
│  │  │  └─────────────────────────────────────────────────────────────────────┘ │  │  │
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
│  │  │  │    • Run Scan (BlackDuckScanActivity)                              │ │  │  │
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
│             │
│  appId: app-123
│  component: api
│  buildId: build-456
│  toolType: BLACKDUCK_DETECT
└──────┬──────┘
       │
       │ ScanRequest
       │ + appId, component, buildId, toolType
       │
       ▼
┌─────────────────────────────────┐
│  SecurityScanClient              │
│  determineTaskQueue()            │
│  generateWorkflowId()            │
│                                  │
│  Workflow ID:                    │
│  app-123-api-build-456-          │
│  blackduck-detect                │
└──────┬──────────────────────────┘
       │
       │ Queue Selection Logic:
       │
       ├─→ Explicit queue set?
       │   YES → Use that queue
       │   NO  ↓
       │
       ├─→ Tool Type = BLACKDUCK?
       │   YES → SECURITY_SCAN_TASK_QUEUE_BLACKDUCK
       │   NO  ↓
       │
       └─→ SECURITY_SCAN_TASK_QUEUE_DEFAULT
       
       │
       ▼
┌─────────────────────────────────┐
│  Temporal Service                │
│  Workflow Started                │
│  Workflow ID: app-123-api-       │
│    build-456-blackduck-detect    │
│  Task Queued to:                 │
│    SECURITY_SCAN_TASK_QUEUE_     │
│    BLACKDUCK                     │
└─────────────────────────────────┘
```

### 2. Worker Task Picking Flow (Scan-Type Based)

```
┌─────────────────────────────────────────────────────────┐
│  Temporal Service                                        │
│  ┌──────────────────────┐  ┌──────────────────────┐     │
│  │ BLACKDUCK QUEUE      │  │ DEFAULT QUEUE        │     │
│  │                      │  │ (Fallback)           │     │
│  │ [app-456-ui-         │  │                      │     │
│  │  build-101-          │  │                      │     │
│  │  blackduck-detect]   │  │                      │     │
│  │                      │  │                      │     │
│  │ [app-789-backend-    │  │                      │     │
│  │  build-202-          │  │                      │     │
│  │  blackduck-detect]   │  │                      │     │
│  └──────┬───────────────┘  └──────┬───────────────┘     │
│         │                        │                     │
│         └────────────┬───────────┘                     │
│                      │                                  │
└──────────────────────┼──────────────────────────────────┘
                       │
                       │ Polling
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ▼              ▼              ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ BlackDuck    │  │ Default      │  │              │
│ Worker       │  │ Worker       │  │              │
│              │  │ (Fallback)   │  │              │
│ SCAN_TYPE=   │  │              │  │              │
│ BLACKDUCK_   │  │              │  │              │
│ DETECT       │  │              │  │              │
│              │  │              │  │              │
│ Polls:       │  │ Polls:       │  │              │
│ • BlackDuck  │  │ • Default    │  │              │
│   Queue      │  │   Queue      │  │              │
│              │  │              │  │              │
│ Picks:       │  │ Picks:       │  │              │
│ app-456-ui-  │  │ (Fallback)   │  │              │
│ build-101-   │  │              │  │              │
│ blackduck-   │  │              │  │              │
│ detect       │  │              │  │              │
└──────┬───────┘  └──────┬───────┘  └──────────────┘
       │                  │
       │ Execute          │ Execute
       │ Workflow         │ Workflow
       │                  │
       ▼                  ▼
┌─────────────────────────────────────────────────────┐
│  Workflow Execution                                 │
│  Workflow ID: app-456-ui-build-101-blackduck-detect │
│  • Clone Repository (shared PVC)                     │
│  • Run BlackDuck Scan                                │
│  • Store Results                                     │
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
       ├─→ BlackDuck Queue
       └─→ Default Queue (fallback)
       
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
- **Logic**: Scan-type based → Default
- **Result**: Workflow routed to appropriate queue

### 2. Worker Polling
- **Location**: `SecurityScanWorker`
- **Configuration**: `TASK_QUEUES` environment variable
- **Default**: Polls all queues
- **Custom**: Polls specified queues (comma-separated)

### 3. Task Execution
- **Workflow**: `SecurityScanWorkflowImpl`
- **Activities**: Repository, BlackDuck, Storage
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
