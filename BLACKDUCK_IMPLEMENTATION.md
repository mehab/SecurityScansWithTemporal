# BlackDuck Scan Implementation

## Overview

The BlackDuck scan implementation has been updated to support hub-based scanning with dynamic hub URL determination and support for rapid scans.

## Input Fields

Each BlackDuck scan request requires the following fields in `BlackDuckConfig`:

1. **ALM** - ALM identifier
2. **App ID** - Application ID (from ScanRequest)
3. **Component** - Component name (from ScanRequest)
4. **Build ID** - Build ID (from ScanRequest)
5. **SrcFilename** - Source filename
6. **SrcURL** - Source URL (repository URL from ScanRequest)
7. **Source_SYSTEM** - Source system identifier
8. **Transid** - Transaction ID
9. **Scan_Source_type** - Scan source type (e.g., "RAPID", "FULL")

## Hub URL Determination

The BlackDuck scan activity determines the appropriate hub URL using the following priority:

1. **Explicit Hub URL** - If `blackDuckConfig.setHubUrl()` is set, use it
2. **ALM-based** - Map ALM identifier to hub URL (implement `mapAlmToHubUrl()`)
3. **Source System-based** - Map source system to hub URL (implement `mapSourceSystemToHubUrl()`)
4. **Component-based** - Map component/appId to hub URL (implement `mapComponentToHubUrl()`)
5. **Default from ScanConfig** - Use `scanConfig.getBlackduckUrl()` if available
6. **Error** - Throw exception if no hub URL can be determined

### Implementing Hub URL Mapping

You need to implement the mapping methods in `BlackDuckScanActivityImpl`:

```java
private String mapAlmToHubUrl(String alm) {
    // Example implementation:
    if ("ALM-001".equals(alm)) {
        return "https://hub1.blackduck.com";
    } else if ("ALM-002".equals(alm)) {
        return "https://hub2.blackduck.com";
    }
    return null;
}

private String mapSourceSystemToHubUrl(String sourceSystem) {
    // Example implementation:
    if ("CI_CD_SYSTEM".equals(sourceSystem)) {
        return "https://hub1.blackduck.com";
    }
    return null;
}

private String mapComponentToHubUrl(String appId, String component) {
    // Example implementation:
    // Could be based on component name patterns, appId ranges, etc.
    if (component != null && component.startsWith("api-")) {
        return "https://hub1.blackduck.com";
    }
    return null;
}
```

## Rapid Scan Detection

The activity determines if a scan is a rapid scan based on:

1. **Explicit Setting** - If `blackDuckConfig.setRapidScan(true)` is set
2. **Scan Source Type** - If `scanSourceType` contains "RAPID" or "QUICK"
3. **Default** - Full scan (not rapid)

### Rapid Scan vs Full Scan

- **Rapid Scan**: Results available immediately after scan completes
- **Full Scan**: Results may need to be polled from the Hub after scan completes

The activity sets metadata in the scan result:
- `scanType`: "RAPID" or "FULL"
- `resultsAvailable`: "true" (rapid) or "false" (full)
- `pollRequired`: "true" (full scan) or not set (rapid scan)

## Detect Shell Script

The activity uses the BlackDuck Detect shell script (`detect.sh` or `detect8.sh`) to perform scans.

### Script Location

The activity searches for the detect script in these locations:
- `/usr/local/bin/detect.sh`
- `/usr/local/bin/detect8.sh`
- `/opt/blackduck/detect.sh`
- `/opt/blackduck/detect8.sh`
- `detect.sh` (current directory)
- `detect8.sh` (current directory)
- `detect` (command in PATH)

### Detect Command Parameters

The activity builds the detect command with these parameters:

**Required:**
- `--detect.source.path`: Repository path
- `--blackduck.url`: Hub URL (or via `BLACKDUCK_URL` env var)
- `--blackduck.api.token`: Hub API token (or via `BLACKDUCK_API_TOKEN` env var)

**Optional:**
- `--detect.project.name`: Project name
- `--detect.project.version.name`: Project version
- `--detect.rapid.scan.mode`: Enable rapid scan mode
- `--detect.output.path`: Output directory
- `--detect.code.location.name`: Code location name (uses TransId if available)
- `--detect.cleanup`: Cleanup after scan
- `--detect.tools`: Scan tools (set to `SIGNATURE_SCAN`)

**Environment Variables:**
- `BLACKDUCK_API_TOKEN`: Hub API token
- `BLACKDUCK_URL`: Hub URL
- `DETECT_ALM`: ALM identifier
- `DETECT_SOURCE_SYSTEM`: Source system
- `DETECT_TRANS_ID`: Transaction ID
- `DETECT_SCAN_SOURCE_TYPE`: Scan source type

## Usage Example

```java
// Create BlackDuck scan client
BlackDuckScanClient client = new BlackDuckScanClient("temporal-service:7233");

// Create BlackDuck-specific configuration
BlackDuckConfig blackDuckConfig = new BlackDuckConfig();
blackDuckConfig.setAlm("ALM-001");
blackDuckConfig.setSrcFilename("my-repo");
blackDuckConfig.setSourceSystem("CI_CD_SYSTEM");
blackDuckConfig.setTransId("trans-123456");
blackDuckConfig.setScanSourceType("RAPID");
blackDuckConfig.setRapidScan(true);

// Hub configuration (optional - will be determined if not set)
// blackDuckConfig.setHubUrl("https://hub1.blackduck.com");
// blackDuckConfig.setHubApiToken("token");

// Project configuration
blackDuckConfig.setProjectName("my-project");
blackDuckConfig.setProjectVersion("1.0.0");

// Create scan request
ScanRequest request = client.createScanRequest(
    "app-123",           // App ID
    "api-component",    // Component
    "build-456",         // Build ID
    "https://github.com/example/repo.git", // SrcURL
    "main",              // Branch
    "abc123def456",      // Commit SHA
    blackDuckConfig      // BlackDuck config
);

// Configure scan settings
ScanConfig config = new ScanConfig();
config.setCloneStrategy(CloneStrategy.SHALLOW_SINGLE_BRANCH);
request.setScanConfig(config);

// Submit scan
String workflowId = client.submitScan(request);
```

## Scan Flow

1. **Client receives request** with BlackDuckConfig
2. **Workflow starts** and clones repository
3. **BlackDuck activity executes**:
   - Determines hub URL
   - Determines if rapid scan
   - Executes detect shell script
   - Extracts scan results
4. **Results returned** with metadata indicating scan type and result availability

## Scan Results

### Rapid Scan Results

- Results available immediately after scan completes
- `resultsAvailable`: "true"
- `rapidScanResults`: Path to rbdump.json file (if available)

### Full Scan Results

- Results may need to be polled from Hub
- `resultsAvailable`: "false"
- `pollRequired`: "true"
- `hubUrl`: Hub URL for polling results
- `bdioFile`: Path to bdio.json file

## Output Files

Detect creates output files in `{workspacePath}/blackduck-output/`:

- `bdio.json` - Scan results (BDIO format)
- `scan.log` - Scan log
- `rbdump.json` - Rapid scan results (if rapid scan)

## Configuration

### Hub URL Mapping

Implement hub URL determination logic in `BlackDuckScanActivityImpl`:

```java
// In BlackDuckScanActivityImpl.java
private String mapAlmToHubUrl(String alm) {
    // Your implementation here
    // Could use a configuration file, database, or hardcoded mapping
}
```

### Detect Script Installation

Ensure the detect script is installed in one of the expected locations or available in PATH.

The detect script can be downloaded from:
- BlackDuck Hub UI
- BlackDuck API
- Pre-installed in container image

## Best Practices

1. **Hub URL Mapping**: Implement robust hub URL determination based on your organization's structure
2. **Rapid Scan**: Use rapid scans for smaller repositories or when quick results are needed
3. **Full Scan**: Use full scans for comprehensive analysis
4. **Error Handling**: Handle cases where hub URL cannot be determined
5. **Result Polling**: For full scans, implement result polling if needed (outside this activity)

## Troubleshooting

### Hub URL Not Determined

- Ensure at least one mapping method is implemented
- Or set hub URL explicitly in BlackDuckConfig
- Or set default hub URL in ScanConfig

### Detect Script Not Found

- Ensure detect script is installed in one of the expected locations
- Or ensure `detect` command is in PATH
- Check container image includes detect script

### Rapid Scan Results Not Available

- Verify rapid scan mode is enabled
- Check detect output for rapid scan completion
- Verify rbdump.json file is created
