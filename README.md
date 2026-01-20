# Security Scan Project - Temporal Workflow

A comprehensive security scanning system built with Temporal workflows and activities, designed for space-efficient execution on Kubernetes clusters with limited PVC storage.

## Overview

This project implements a distributed security scanning system that orchestrates application security scans:

- **BlackDuck Detect Signature Scanning**: Identifies open-source components and vulnerabilities
  - Supports hub-based scanning with dynamic hub URL determination
  - Supports rapid scan mode for faster results
  - Requires ALM, source system, transaction ID, and other BlackDuck-specific fields

**Note**: The application structure is designed to support multiple scan types. Currently only BlackDuck Detect is implemented, but additional scan types can be added in the future.

## Architecture

### Design Principles

1. **Space Efficiency**: Designed to work with limited PVC storage on Kubernetes clusters
2. **Separation of Concerns**: Temporal service runs on separate cluster from workers
3. **Scalability**: Activities can run on different Kubernetes pods for horizontal scaling
4. **Fault Tolerance**: Built-in retry mechanisms and cleanup on failures

### Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Temporal Service Cluster                  │
│  (Separate Kubernetes cluster - manages workflow orchestration) │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ Workflow Tasks
                            │
┌─────────────────────────────────────────────────────────────┐
│                  Worker Kubernetes Cluster(s)                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  SecurityScanWorker                                   │  │
│  │  - Polls task queue                                   │  │
│  │  - Executes workflows and activities                  │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  Activities:                                                 │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │ Repository   │  │  BlackDuck   │                        │
│  │ Activity     │  │  Activity    │                        │
│  └──────────────┘  └──────────────┘                        │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  PVC Storage (Limited)                                │  │
│  │  /workspace/security-scans/{scan-id}/                │  │
│  │    ├── repo/          (cloned repository)             │  │
│  │    └── blackduck-output/                            │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Workflow Execution Flow

1. **Repository Cloning**: Clone repository from SCM (Git) to workspace
2. **Space Check**: Verify available space before operations (includes CLI tools, scan outputs, temp files)
3. **Scan Execution**: Execute BlackDuck Detect scan on the cloned repository
4. **Result Aggregation**: Collect scan results
5. **External Storage**: Store scan results and reports to external storage (if configured)
6. **Cleanup**: Remove workspace to free space (configurable)

### Space Management Strategy

- **Workspace Isolation**: Each scan gets a unique workspace directory
- **Automatic Cleanup**: Workspace is cleaned after scan completion
- **Space Monitoring**: Check available space before cloning (accounts for CLI tools, outputs, temp files)
- **Configurable Cleanup**: Option to cleanup after scan completion (space-efficient mode)
- **CLI Tool Space**: Accounts for BlackDuck Detect JAR (~100MB) in space calculations

### Execution Model

The system executes a single BlackDuck Detect scan per workflow:
- **Single Scan Per Workflow**: Each workflow execution handles one BlackDuck scan
- **Space-efficient**: Uses single repository clone
- **Isolated Execution**: Each scan runs in its own workspace directory
- **Configurable Timeouts**: Set per-scan and workflow-level timeouts to prevent long-running scans

**Note**: The application structure is designed to support multiple scan types in the future. Currently, only BlackDuck Detect is implemented.

## Components

### Workflows

- **SecurityScanWorkflow**: Main orchestration workflow that coordinates BlackDuck Detect scanning

### Activities

- **RepositoryActivity**: Manages repository cloning and workspace cleanup
- **BlackDuckScanActivity**: Performs BlackDuck Detect signature scanning
  - Determines appropriate hub URL based on scan request (ALM, source system, or component)
  - Executes detect shell script with proper parameters
  - Handles rapid scan vs full scan modes
  - Extracts scan results based on scan type (rapid scan results available immediately, full scan may require polling)
- **StorageActivity**: Stores scan results and reports to external storage (local filesystem or object storage)

### Data Models

- **ScanRequest**: Contains repository information and scan configuration
- **ScanResult**: Result of individual scan execution
- **ScanSummary**: Summary of scan results
- **ScanConfig**: Configuration for scans (credentials, paths, etc.)
- **BlackDuckConfig**: BlackDuck-specific configuration (ALM, source system, transaction ID, hub URL, rapid scan settings)

## Prerequisites

### Runtime Dependencies

The following CLI tools are pre-installed in the container image (see Dockerfile):

1. **Git**: For repository cloning
2. **BlackDuck Detect**: For signature scanning (detect script installed, JAR downloaded on first run)

**Note**: CLI tools are installed in the container image at build time. To update tools, rebuild the container image with new versions. See Dockerfile for installation details.

### Environment Variables

- `TEMPORAL_ADDRESS`: Address of Temporal service (e.g., `temporal.example.com:7233`)
- `WORKSPACE_BASE_DIR`: Base directory for workspaces (default: `/workspace/security-scans`)

## Building

```bash
cd security-scan-project
mvn clean compile
mvn package
```

## Running

### Start Worker

```bash
# Set Temporal service address
export TEMPORAL_ADDRESS=temporal.example.com:7233

# Run worker
java -jar target/security-scan-1.0.0.jar
```

Or using Maven:

```bash
mvn exec:java -Dexec.mainClass="securityscanapp.SecurityScanWorker"
```

### Trigger Scan (Client)

#### Using Generic Client (Legacy)

```bash
mvn exec:java -Dexec.mainClass="securityscanapp.SecurityScanClient"
```

#### Using Dedicated Scan Clients (Recommended)

**BlackDuck Scan Client:**
```bash
mvn exec:java -Dexec.mainClass="securityscanapp.BlackDuckScanClient"
```

See [SCAN_CLIENTS.md](SCAN_CLIENTS.md) for detailed usage examples.

## Deployment on Kubernetes

### Helm Chart (Recommended)

The easiest way to deploy the Security Scan application is using the provided Helm chart:

```bash
# Install with default values
helm install security-scan ./helm/security-scan

# Install with custom values
helm install security-scan ./helm/security-scan -f my-values.yaml

# Install to a specific namespace
helm install security-scan ./helm/security-scan --namespace security-scan --create-namespace
```

See [helm/security-scan/README.md](helm/security-scan/README.md) for detailed Helm chart documentation.

### Manual Kubernetes Deployment

Alternatively, you can deploy using the raw Kubernetes manifests:

```bash
# Apply all manifests
kubectl apply -f kubernetes-deployment.yaml
kubectl apply -f kubernetes-cronjob-restart-service.yaml
```

### Worker Container Configuration

#### Dockerfile Example

```dockerfile
FROM openjdk:11-jre-slim

# Install dependencies
RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Install BlackDuck Detect
RUN curl -L https://detect.synopsys.com/detect.sh -o /usr/local/bin/detect.sh && \
    chmod +x /usr/local/bin/detect.sh

# Copy application
COPY target/security-scan-1.0.0.jar /app/security-scan.jar

# Create workspace directory
RUN mkdir -p /workspace/security-scans

WORKDIR /app
ENTRYPOINT ["java", "-jar", "security-scan.jar"]
```

#### Kubernetes Deployment

See `kubernetes-deployment.yaml` for complete deployment configuration.

Key requirements:
- **ReadWriteMany (RWX) PVC** for shared storage
- **Same PVC** mounted on all worker pods
- **Same mount path** (`/workspace`) on all pods

### Separate Clusters Configuration

#### Temporal Service Cluster
- Runs Temporal server components
- No worker containers
- Higher availability requirements

#### Worker Cluster(s)
- Runs SecurityScanWorker pods
- Can scale horizontally
- Mounts PVC (RWX) for workspace storage
- Connects to Temporal service cluster

### Storage Configuration

**⚠️ CRITICAL: Shared storage is REQUIRED for this architecture**

Activities can run on different pods, so **shared storage is essential**. All worker pods must mount the same shared storage volume.

#### Why Shared Storage is Required

- **Activities run on different pods**: Temporal dispatches activities to any available worker
- **Shared storage needed**: Activities must access the same repository clone
- **Shared storage**: All activities access the same repository clone on shared storage
- **Without shared storage**: Activities would fail because they can't access files created by other pods

#### Platform-Specific Setup

**For Kubernetes**: Use PVC with ReadWriteMany (RWX)
- **CRITICAL**: Storage class must support `ReadWriteMany` access mode
- See [OPENSHIFT_DEPLOYMENT.md](OPENSHIFT_DEPLOYMENT.md) for detailed setup
- See `kubernetes-deployment.yaml` for worker deployment configuration
- See `kubernetes-cronjob-restart-service.yaml` for workflow restart service (runs every 30 minutes)
- Common options: NFS, CephFS, GlusterFS

#### Storage Requirements

1. **Access Mode**: Must support ReadWriteMany (RWX) for shared access across worker pods
2. **Mount Path**: Same path (`/workspace`) on all worker pods
3. **Same Volume**: All pods mount the same volume/PVC
4. **Performance**: Network storage (NFS) may have slightly higher latency

#### Space Management

- Monitor storage usage
- Set up alerts for storage capacity
- Implement cleanup policies
- Account for storage performance characteristics

## Configuration

### Scan Configuration

```java
ScanConfig config = new ScanConfig();
config.setCleanupAfterEachScan(true);  // Space-efficient mode
config.setMaxWorkspaceSizeBytes(10L * 1024 * 1024 * 1024); // 10GB

// Note: Parallel execution is not applicable since only one scan type (BlackDuck) is supported
// The executeScansInParallel setting is kept for future extensibility but has no effect currently

// Large repository optimization
config.setCloneStrategy(CloneStrategy.SHALLOW_SINGLE_BRANCH); // Space-efficient cloning
config.setShallowCloneDepth(1); // Only latest commit
config.setUseSparseCheckout(true); // Only checkout needed paths
config.addSparseCheckoutPath("src/");
config.addSparseCheckoutPath("*.java");

// External storage for results (local filesystem)
StorageConfig storageConfig = new StorageConfig();
storageConfig.setStorageType(StorageType.LOCAL_FILESYSTEM);
storageConfig.setStorageBasePath("/mnt/storage/scan-results"); // Mounted PVC or NFS
storageConfig.setStoreSummaryJson(true);
storageConfig.setStoreReportFiles(true);
config.setStorageConfig(storageConfig);

// Git credentials (use Kubernetes Secrets in production)
config.setGitUsername("git-user");
config.setGitPassword("git-token");

```

### BlackDuck Configuration

#### New BlackDuck Configuration (Recommended)

For BlackDuck scans, use the dedicated `BlackDuckConfig` class with all required fields:

```java
// Create BlackDuck-specific configuration
BlackDuckConfig blackDuckConfig = new BlackDuckConfig();

// Required BlackDuck input fields
blackDuckConfig.setAlm("ALM-001");                    // ALM identifier
blackDuckConfig.setSrcFilename("my-repo");            // Source filename
blackDuckConfig.setSourceSystem("CI_CD_SYSTEM");     // Source system
blackDuckConfig.setTransId("trans-123456");          // Transaction ID
blackDuckConfig.setScanSourceType("RAPID");           // Scan source type (RAPID or FULL)

// Scan type configuration
blackDuckConfig.setRapidScan(true);                   // Enable rapid scan mode

// Project configuration
blackDuckConfig.setProjectName("project-name");       // BlackDuck project name
blackDuckConfig.setProjectVersion("1.0.0");          // BlackDuck project version

// Hub configuration (optional - will be determined automatically if not set)
// blackDuckConfig.setHubUrl("https://hub1.blackduck.com");  // Explicit hub URL
// blackDuckConfig.setHubApiToken("hub-token");             // Hub API token

// Create scan request with BlackDuck configuration
ScanRequest request = new ScanRequest(
    "app-123",                    // App ID
    "api-component",             // Component
    "build-456",                  // Build ID
    ScanType.BLACKDUCK_DETECT,    // Tool type
    "https://github.com/example/repo.git", // Repository URL (SrcURL)
    "main",                       // Branch
    "abc123def456"                // Commit SHA
);
request.setBlackDuckConfig(blackDuckConfig);
request.setScanConfig(config);
```

#### Hub URL Determination

The BlackDuck scan activity automatically determines the hub URL using this priority:

1. **Explicit Hub URL** - If `blackDuckConfig.setHubUrl()` is set
2. **ALM-based mapping** - Maps ALM identifier to hub URL (implement `mapAlmToHubUrl()`)
3. **Source system-based mapping** - Maps source system to hub URL (implement `mapSourceSystemToHubUrl()`)
4. **Component-based mapping** - Maps component/appId to hub URL (implement `mapComponentToHubUrl()`)
5. **Default from ScanConfig** - Uses `scanConfig.getBlackduckUrl()` if available
6. **Error** - Throws exception if no hub URL can be determined

**Note**: You need to implement the hub URL mapping methods in `BlackDuckScanActivityImpl` based on your organization's structure. See [BLACKDUCK_IMPLEMENTATION.md](BLACKDUCK_IMPLEMENTATION.md) for details.

#### Rapid Scan vs Full Scan

- **Rapid Scan**: Results available immediately after scan completes
  - Set `blackDuckConfig.setRapidScan(true)`
  - Or set `scanSourceType` to "RAPID"
  - Results are in `rbdump.json` file
  
- **Full Scan**: Comprehensive analysis, results may need polling
  - Set `blackDuckConfig.setRapidScan(false)` or don't set it
  - Or set `scanSourceType` to "FULL"
  - Results may need to be polled from the Hub after scan completes

#### Legacy BlackDuck Configuration (Backward Compatibility)

For backward compatibility, you can still use `ScanConfig` for basic BlackDuck settings:

```java
// Legacy BlackDuck configuration (backward compatibility)
config.setBlackduckApiToken("bd-token");
config.setBlackduckUrl("https://blackduck.example.com");
config.setBlackduckProjectName("project-name");
config.setBlackduckProjectVersion("1.0.0");
```

**Note**: The new `BlackDuckConfig` approach is recommended as it provides more control and supports hub-based scanning with dynamic hub URL determination.

### Scan Execution

Each workflow execution performs a single BlackDuck Detect scan:

```java
// Each ScanRequest handles one BlackDuck scan
ScanRequest request = new ScanRequest(
    "app-123",
    "component-api",
    "build-456",
    ScanType.BLACKDUCK_DETECT,  // Single scan type per request
    repositoryUrl,
    branch,
    commitSha
);
```

**Note**: The application currently supports only BlackDuck Detect scanning. The structure is designed to support additional scan types in the future, but parallel execution of multiple scan types is not currently implemented.

### Security Best Practices

1. **Credentials Management**
   - Use Kubernetes Secrets
   - Never hardcode credentials
   - Rotate credentials regularly

2. **Network Security**
   - Use network policies to restrict pod communication
   - Encrypt traffic in transit (TLS)
   - Restrict pod network access

3. **Storage Security**
   - Use encrypted PVCs
   - Implement proper access controls
   - Enable audit logging

## Monitoring and Observability

### Metrics to Monitor

- Workflow execution time
- Activity execution time
- Space usage on PVC
- Scan success/failure rates
- Worker task queue depth

### Logging

- All activities log progress with heartbeats
- Scan results include execution metadata
- Errors are captured in ScanResult objects

### Monitoring Integration

Set up monitoring dashboards for:
- Temporal workflow metrics
- Kubernetes pod metrics
- PVC storage metrics
- Application logs

## Troubleshooting

### Common Issues

1. **Insufficient Space**
   - Check available space before cloning
   - Ensure cleanup is working
   - Monitor PVC usage

2. **Scan Failures**
   - Verify CLI tools are installed
   - Check credentials are valid
   - Review activity logs

3. **Worker Connection Issues**
   - Verify TEMPORAL_ADDRESS is correct
   - Check network connectivity
   - Verify Temporal service is running

## Additional Features

### Single Scan Execution
- ✅ **Implemented**: Each workflow executes a single BlackDuck Detect scan
- Isolated workspace per scan for space efficiency
- Configurable timeouts to prevent long-running scans from blocking the queue
- See [BOTTLENECK_PREVENTION.md](BOTTLENECK_PREVENTION.md) for timeout configuration details

### External Storage
- ✅ **Implemented**: Store scan results and reports to external storage
- Supports local filesystem and object storage backends
- Automatic storage after scan completion
- Configurable storage path and type
- See [TOOL_VERSION_MANAGEMENT.md](TOOL_VERSION_MANAGEMENT.md) for details

### CLI Tools
- CLI tools (BlackDuck Detect) are pre-installed in the container image
- Tools are updated manually by rebuilding the container image with new versions
- No automatic tool updates or version checking during runtime

### BlackDuck Hub-Based Scanning
- ✅ **Implemented**: Dynamic hub URL determination based on ALM, source system, or component
- ✅ **Implemented**: Rapid scan support for faster results
- ✅ **Implemented**: Automatic detect script discovery and execution
- ✅ **Implemented**: Support for all BlackDuck input fields (ALM, App ID, Component, Build ID, SrcFilename, SrcURL, Source System, Trans ID, Scan Source Type)
- Hub URL is determined automatically by the scan activity using configurable mapping strategies
- Rapid scans provide immediate results; full scans may require result polling
- See [BLACKDUCK_IMPLEMENTATION.md](BLACKDUCK_IMPLEMENTATION.md) for detailed information

### Large Repository Support
- ✅ **Implemented**: Space-efficient cloning strategies
- Shallow clone, single branch, sparse checkout
- Accounts for CLI tool sizes in space calculations

### Failure Handling
- ✅ **Implemented**: Comprehensive failure handling for all OpenShift scenarios
- Storage failures: Detected and workflows marked for restart
- Pod failures: Handled by Temporal retries
- Network failures: Handled by Temporal SDK
- Resource exhaustion: Handled by Kubernetes + Temporal
- Deployment failures: Detected at worker startup
- See [FAILURE_HANDLING.md](FAILURE_HANDLING.md) for details

### Bottleneck Prevention
- ✅ **Implemented**: Multiple strategies to prevent long-running scans from blocking the queue
- Configurable activity timeouts: Set per-scan timeout limits
- Workflow execution timeout: Set maximum workflow execution time
- Scan-type specific task queues: BlackDuck scans use dedicated queue
- Worker pool scaling: Scale workers horizontally to handle load
- See [BOTTLENECK_PREVENTION.md](BOTTLENECK_PREVENTION.md) for details

## Documentation

- [APPLICATION_STRUCTURE.md](APPLICATION_STRUCTURE.md): **Application structure** - appId, component, buildId, toolType structure and usage
- [SCAN_CLIENTS.md](SCAN_CLIENTS.md): **Scan orchestrator clients** - How to use dedicated BlackDuck scan client
- [BLACKDUCK_IMPLEMENTATION.md](BLACKDUCK_IMPLEMENTATION.md): **BlackDuck scan implementation** - Hub URL determination, rapid scan support, and Detect script usage
- [ARCHITECTURE_DIAGRAM.md](ARCHITECTURE_DIAGRAM.md): **Visual architecture diagrams** - routing, task picking, failure handling, and restart service
- [ARCHITECTURE_EXPLANATION.md](ARCHITECTURE_EXPLANATION.md): How the application works - architecture, execution flow, and components
- [LARGE_REPO_STRATEGY.md](LARGE_REPO_STRATEGY.md): How large repositories are handled efficiently
- [FAILURE_HANDLING.md](FAILURE_HANDLING.md): How failures are detected and handled
- [BOTTLENECK_PREVENTION.md](BOTTLENECK_PREVENTION.md): How to prevent long-running scans from blocking the queue
- [QUEUE_ROUTING.md](QUEUE_ROUTING.md): How automatic task queue routing works
- [WORKFLOW_RESTART_USAGE.md](WORKFLOW_RESTART_USAGE.md): How to use WorkflowRestartClient to restart failed workflows
- [OPENSHIFT_DEPLOYMENT.md](OPENSHIFT_DEPLOYMENT.md): How to deploy on OpenShift

## Future Enhancements

- Incremental scanning (only scan changed files)
- Integration with CI/CD pipelines
- Support for additional scan types
- Dynamic worker scaling based on queue depth
- Advanced space optimization techniques

## License

[Your License Here]

