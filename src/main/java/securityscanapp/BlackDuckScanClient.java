package securityscanapp;

/**
 * BlackDuck scan orchestrator client
 * Dedicated client for submitting BlackDuck Detect scan requests
 * Routes requests to SECURITY_SCAN_TASK_QUEUE_BLACKDUCK
 */
public class BlackDuckScanClient extends BaseScanClient {
    
    /**
     * Create a BlackDuck scan client
     * 
     * @param temporalAddress Temporal service address (e.g., "localhost:7233" or "temporal-service:7233")
     */
    public BlackDuckScanClient(String temporalAddress) {
        super(temporalAddress, ScanType.BLACKDUCK_DETECT);
    }
    
    /**
     * Create a BlackDuck scan client with default Temporal address
     * Uses environment variable TEMPORAL_ADDRESS or defaults to "localhost:7233"
     */
    public BlackDuckScanClient() {
        this(getTemporalAddress());
    }
    
    /**
     * Get Temporal address from environment or use default
     */
    private static String getTemporalAddress() {
        String temporalAddress = System.getenv("TEMPORAL_ADDRESS");
        if (temporalAddress == null || temporalAddress.isEmpty()) {
            temporalAddress = "localhost:7233"; // Default for local development
        }
        return temporalAddress;
    }
    
    /**
     * Create a scan request for BlackDuck scanning with all required BlackDuck fields
     * 
     * @param appId Application ID
     * @param component Component name
     * @param buildId Build ID
     * @param repositoryUrl Repository URL (SrcURL)
     * @param branch Branch name
     * @param commitSha Commit SHA (optional, can be null)
     * @param blackDuckConfig BlackDuck-specific configuration
     * @return ScanRequest configured for BlackDuck scanning
     */
    public ScanRequest createScanRequest(String appId, String component, String buildId,
                                         String repositoryUrl, String branch, String commitSha,
                                         BlackDuckConfig blackDuckConfig) {
        ScanRequest request = new ScanRequest(
            appId,
            component,
            buildId,
            ScanType.BLACKDUCK_DETECT,
            repositoryUrl,
            branch,
            commitSha
        );
        
        // Set BlackDuck-specific fields in config
        blackDuckConfig.setAppId(appId);
        blackDuckConfig.setComponent(component);
        blackDuckConfig.setBuildId(buildId);
        blackDuckConfig.setSrcUrl(repositoryUrl);
        
        // Set srcFilename from repository URL if not provided
        if (blackDuckConfig.getSrcFilename() == null && repositoryUrl != null) {
            // Extract filename from URL (e.g., repo name)
            String filename = extractFilenameFromUrl(repositoryUrl);
            blackDuckConfig.setSrcFilename(filename);
        }
        
        request.setBlackDuckConfig(blackDuckConfig);
        
        return request;
    }
    
    /**
     * Create a scan request with minimal parameters (backward compatibility)
     * BlackDuckConfig must be set separately
     */
    public ScanRequest createScanRequest(String appId, String component, String buildId,
                                         String repositoryUrl, String branch, String commitSha) {
        ScanRequest request = new ScanRequest(
            appId,
            component,
            buildId,
            ScanType.BLACKDUCK_DETECT,
            repositoryUrl,
            branch,
            commitSha
        );
        
        // Create minimal BlackDuckConfig
        BlackDuckConfig blackDuckConfig = new BlackDuckConfig();
        blackDuckConfig.setAppId(appId);
        blackDuckConfig.setComponent(component);
        blackDuckConfig.setBuildId(buildId);
        blackDuckConfig.setSrcUrl(repositoryUrl);
        
        request.setBlackDuckConfig(blackDuckConfig);
        
        return request;
    }
    
    /**
     * Extract filename from repository URL
     */
    private String extractFilenameFromUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isEmpty()) {
            return null;
        }
        
        // Extract repo name from URL
        // e.g., https://github.com/user/repo.git -> repo
        String url = repositoryUrl;
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }
        
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        
        return url;
    }
}
