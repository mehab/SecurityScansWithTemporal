package securityscanapp;

/**
 * Request object containing all information needed to perform security scans
 * 
 * Structure:
 * - Application ID: Identifies the application
 * - Component: Component within the application
 * - Build ID: Unique build identifier for the component
 * - Tool Type: Single scan type (Gitleaks, BlackDuck, etc.)
 * 
 * Workflow ID format: {appId}-{component}-{buildId}-{toolType}
 */
public class ScanRequest {
    private String appId; // Application ID
    private String component; // Component name
    private String buildId; // Build ID (unique per component build)
    private ScanType toolType; // Single scan tool type (Gitleaks, BlackDuck, etc.)
    
    private String scanId; // Generated from appId-component-buildId-toolType
    private String repositoryUrl;
    private String branch;
    private String commitSha;
    private String workspacePath; // Unique workspace path for this scan
    private ScanConfig scanConfig;
    private BlackDuckConfig blackDuckConfig; // BlackDuck-specific configuration (only for BlackDuck scans)
    
    public ScanRequest() {
    }
    
    /**
     * Constructor with appId, component, buildId, toolType
     */
    public ScanRequest(String appId, String component, String buildId, ScanType toolType, 
                       String repositoryUrl, String branch, String commitSha) {
        this.appId = appId;
        this.component = component;
        this.buildId = buildId;
        this.toolType = toolType;
        this.repositoryUrl = repositoryUrl;
        this.branch = branch;
        this.commitSha = commitSha;
        
        // Generate scanId and workspacePath from structure
        this.scanId = generateScanId(appId, component, buildId, toolType);
        this.workspacePath = Shared.WORKSPACE_BASE_DIR + "/" + this.scanId;
    }
    
    /**
     * Generate scan ID from appId, component, buildId, and toolType
     */
    private String generateScanId(String appId, String component, String buildId, ScanType toolType) {
        return appId + "-" + component + "-" + buildId + "-" + toolType.getId();
    }
    
    /**
     * Generate workflow ID from appId, component, buildId, and toolType
     */
    public String generateWorkflowId() {
        if (appId == null || component == null || buildId == null || toolType == null) {
            throw new IllegalStateException("Cannot generate workflow ID: appId, component, buildId, and toolType must all be set");
        }
        return appId + "-" + component + "-" + buildId + "-" + toolType.getId();
    }
    
    // Getters and Setters
    public String getScanId() {
        return scanId;
    }
    
    public void setScanId(String scanId) {
        this.scanId = scanId;
        if (this.workspacePath == null) {
            this.workspacePath = Shared.WORKSPACE_BASE_DIR + "/" + scanId;
        }
    }
    
    // Getters and Setters for new structure
    public String getAppId() {
        return appId;
    }
    
    public void setAppId(String appId) {
        this.appId = appId;
        // Regenerate scanId if all components are available
        if (component != null && buildId != null && toolType != null) {
            this.scanId = generateScanId(appId, component, buildId, toolType);
            this.workspacePath = Shared.WORKSPACE_BASE_DIR + "/" + this.scanId;
        }
    }
    
    public String getComponent() {
        return component;
    }
    
    public void setComponent(String component) {
        this.component = component;
        // Regenerate scanId if all components are available
        if (appId != null && buildId != null && toolType != null) {
            this.scanId = generateScanId(appId, component, buildId, toolType);
            this.workspacePath = Shared.WORKSPACE_BASE_DIR + "/" + this.scanId;
        }
    }
    
    public String getBuildId() {
        return buildId;
    }
    
    public void setBuildId(String buildId) {
        this.buildId = buildId;
        // Regenerate scanId if all components are available
        if (appId != null && component != null && toolType != null) {
            this.scanId = generateScanId(appId, component, buildId, toolType);
            this.workspacePath = Shared.WORKSPACE_BASE_DIR + "/" + this.scanId;
        }
    }
    
    public ScanType getToolType() {
        return toolType;
    }
    
    public void setToolType(ScanType toolType) {
        this.toolType = toolType;
        // Regenerate scanId if all components are available
        if (appId != null && component != null && buildId != null) {
            this.scanId = generateScanId(appId, component, buildId, toolType);
            this.workspacePath = Shared.WORKSPACE_BASE_DIR + "/" + this.scanId;
        }
    }
    
    public String getRepositoryUrl() {
        return repositoryUrl;
    }
    
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
    
    public String getBranch() {
        return branch;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public String getCommitSha() {
        return commitSha;
    }
    
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
    
    public String getWorkspacePath() {
        return workspacePath;
    }
    
    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
    }
    
    
    public ScanConfig getScanConfig() {
        return scanConfig;
    }
    
    public void setScanConfig(ScanConfig scanConfig) {
        this.scanConfig = scanConfig;
    }
    
    public BlackDuckConfig getBlackDuckConfig() {
        return blackDuckConfig;
    }
    
    public void setBlackDuckConfig(BlackDuckConfig blackDuckConfig) {
        this.blackDuckConfig = blackDuckConfig;
    }
}

