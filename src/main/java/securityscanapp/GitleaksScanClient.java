package securityscanapp;

/**
 * Gitleaks scan orchestrator client
 * Dedicated client for submitting Gitleaks scan requests
 * Routes requests to SECURITY_SCAN_TASK_QUEUE_GITLEAKS
 * 
 * Supports both GITLEAKS_SECRETS and GITLEAKS_FILE_HASH scan types
 */
public class GitleaksScanClient extends BaseScanClient {
    
    private final ScanType gitleaksScanType;
    
    /**
     * Create a Gitleaks scan client for secrets scanning
     * 
     * @param temporalAddress Temporal service address (e.g., "localhost:7233" or "temporal-service:7233")
     */
    public GitleaksScanClient(String temporalAddress) {
        this(temporalAddress, ScanType.GITLEAKS_SECRETS);
    }
    
    /**
     * Create a Gitleaks scan client with specific scan type
     * 
     * @param temporalAddress Temporal service address
     * @param scanType Gitleaks scan type (GITLEAKS_SECRETS or GITLEAKS_FILE_HASH)
     */
    public GitleaksScanClient(String temporalAddress, ScanType scanType) {
        super(temporalAddress, scanType);
        if (scanType != ScanType.GITLEAKS_SECRETS && scanType != ScanType.GITLEAKS_FILE_HASH) {
            throw new IllegalArgumentException(
                "GitleaksScanClient only supports GITLEAKS_SECRETS or GITLEAKS_FILE_HASH. " +
                "Provided: " + scanType
            );
        }
        this.gitleaksScanType = scanType;
    }
    
    /**
     * Create a Gitleaks scan client with default Temporal address
     * Uses environment variable TEMPORAL_ADDRESS or defaults to "localhost:7233"
     * Defaults to GITLEAKS_SECRETS scan type
     */
    public GitleaksScanClient() {
        this(getTemporalAddress(), ScanType.GITLEAKS_SECRETS);
    }
    
    /**
     * Create a Gitleaks scan client with specific scan type and default Temporal address
     * 
     * @param scanType Gitleaks scan type (GITLEAKS_SECRETS or GITLEAKS_FILE_HASH)
     */
    public GitleaksScanClient(ScanType scanType) {
        this(getTemporalAddress(), scanType);
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
     * Create a scan request for Gitleaks secrets scanning
     * Helper method to create a properly configured request
     * 
     * @param appId Application ID
     * @param component Component name
     * @param buildId Build ID
     * @param repositoryUrl Repository URL
     * @param branch Branch name
     * @param commitSha Commit SHA (optional, can be null)
     * @return ScanRequest configured for Gitleaks scanning
     */
    public ScanRequest createScanRequest(String appId, String component, String buildId,
                                         String repositoryUrl, String branch, String commitSha) {
        return new ScanRequest(
            appId,
            component,
            buildId,
            gitleaksScanType,
            repositoryUrl,
            branch,
            commitSha
        );
    }
    
    /**
     * Get the Gitleaks scan type this client is configured for
     */
    public ScanType getGitleaksScanType() {
        return gitleaksScanType;
    }
}
