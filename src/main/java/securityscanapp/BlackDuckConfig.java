package securityscanapp;

/**
 * BlackDuck-specific configuration for scan requests
 * Contains all fields required by BlackDuck Detect
 */
public class BlackDuckConfig {
    private String alm; // ALM identifier
    private String appId; // Application ID (from ScanRequest)
    private String component; // Component name (from ScanRequest)
    private String buildId; // Build ID (from ScanRequest)
    private String srcFilename; // Source filename
    private String srcUrl; // Source URL (repository URL from ScanRequest)
    private String sourceSystem; // Source system identifier
    private String transId; // Transaction ID
    private String scanSourceType; // Scan source type
    
    // Hub configuration (determined by scan activity)
    private String hubUrl; // BlackDuck Hub URL (determined dynamically)
    private String hubApiToken; // Hub API token
    
    // Scan type
    private boolean rapidScan; // Whether this is a rapid scan
    
    // Project configuration (for Detect)
    private String projectName; // BlackDuck project name
    private String projectVersion; // BlackDuck project version
    
    public BlackDuckConfig() {
    }
    
    // Getters and Setters
    public String getAlm() {
        return alm;
    }
    
    public void setAlm(String alm) {
        this.alm = alm;
    }
    
    public String getAppId() {
        return appId;
    }
    
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    public String getComponent() {
        return component;
    }
    
    public void setComponent(String component) {
        this.component = component;
    }
    
    public String getBuildId() {
        return buildId;
    }
    
    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }
    
    public String getSrcFilename() {
        return srcFilename;
    }
    
    public void setSrcFilename(String srcFilename) {
        this.srcFilename = srcFilename;
    }
    
    public String getSrcUrl() {
        return srcUrl;
    }
    
    public void setSrcUrl(String srcUrl) {
        this.srcUrl = srcUrl;
    }
    
    public String getSourceSystem() {
        return sourceSystem;
    }
    
    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }
    
    public String getTransId() {
        return transId;
    }
    
    public void setTransId(String transId) {
        this.transId = transId;
    }
    
    public String getScanSourceType() {
        return scanSourceType;
    }
    
    public void setScanSourceType(String scanSourceType) {
        this.scanSourceType = scanSourceType;
    }
    
    public String getHubUrl() {
        return hubUrl;
    }
    
    public void setHubUrl(String hubUrl) {
        this.hubUrl = hubUrl;
    }
    
    public String getHubApiToken() {
        return hubApiToken;
    }
    
    public void setHubApiToken(String hubApiToken) {
        this.hubApiToken = hubApiToken;
    }
    
    public boolean isRapidScan() {
        return rapidScan;
    }
    
    public void setRapidScan(boolean rapidScan) {
        this.rapidScan = rapidScan;
    }
    
    public String getProjectName() {
        return projectName;
    }
    
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
    
    public String getProjectVersion() {
        return projectVersion;
    }
    
    public void setProjectVersion(String projectVersion) {
        this.projectVersion = projectVersion;
    }
}
