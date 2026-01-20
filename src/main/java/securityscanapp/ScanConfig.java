package securityscanapp;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for security scans
 */
public class ScanConfig {
    private String gitUsername;
    private String gitPassword; // Should use secure credential management in production
    private String gitleaksConfigPath; // Deprecated: Gitleaks removed - kept for future extensibility
    private String blackduckApiToken;
    private String blackduckUrl;
    private String blackduckProjectName;
    private String blackduckProjectVersion;
    private boolean cleanupAfterEachScan; // Deprecated: Not used - cleanup happens after scan completion
    private long maxWorkspaceSizeBytes;
    // Note: executeScansInParallel removed - not applicable with single scan type
    private CloneStrategy cloneStrategy; // Strategy for cloning large repos
    private int shallowCloneDepth; // Depth for shallow clone (1 = only latest commit)
    private boolean useSparseCheckout; // Use sparse checkout for large repos
    private List<String> sparseCheckoutPaths; // Paths to include in sparse checkout
    private StorageConfig storageConfig; // Configuration for storing results to external storage
    private Integer scanTimeoutSeconds; // Per-scan timeout in seconds (null = use default)
    private Integer workflowTimeoutSeconds; // Workflow execution timeout in seconds (null = no timeout)
    private String taskQueue; // Task queue name (null = auto-determined based on scan type)
    
    public ScanConfig() {
        this.cleanupAfterEachScan = true; // Deprecated: Not used - kept for backward compatibility
        this.maxWorkspaceSizeBytes = Shared.MAX_WORKSPACE_SIZE_BYTES;
        this.cloneStrategy = CloneStrategy.SHALLOW; // Default to shallow for space efficiency
        this.shallowCloneDepth = 1; // Only latest commit by default
        this.useSparseCheckout = false; // Disabled by default
        this.sparseCheckoutPaths = new ArrayList<>();
    }
    
    // Getters and Setters
    public String getGitUsername() {
        return gitUsername;
    }
    
    public void setGitUsername(String gitUsername) {
        this.gitUsername = gitUsername;
    }
    
    public String getGitPassword() {
        return gitPassword;
    }
    
    public void setGitPassword(String gitPassword) {
        this.gitPassword = gitPassword;
    }
    
    /**
     * @deprecated Gitleaks has been removed. Kept for future extensibility.
     */
    @Deprecated
    public String getGitleaksConfigPath() {
        return gitleaksConfigPath;
    }
    
    /**
     * @deprecated Gitleaks has been removed. Kept for future extensibility.
     */
    @Deprecated
    public void setGitleaksConfigPath(String gitleaksConfigPath) {
        this.gitleaksConfigPath = gitleaksConfigPath;
    }
    
    public String getBlackduckApiToken() {
        return blackduckApiToken;
    }
    
    public void setBlackduckApiToken(String blackduckApiToken) {
        this.blackduckApiToken = blackduckApiToken;
    }
    
    public String getBlackduckUrl() {
        return blackduckUrl;
    }
    
    public void setBlackduckUrl(String blackduckUrl) {
        this.blackduckUrl = blackduckUrl;
    }
    
    public String getBlackduckProjectName() {
        return blackduckProjectName;
    }
    
    public void setBlackduckProjectName(String blackduckProjectName) {
        this.blackduckProjectName = blackduckProjectName;
    }
    
    public String getBlackduckProjectVersion() {
        return blackduckProjectVersion;
    }
    
    public void setBlackduckProjectVersion(String blackduckProjectVersion) {
        this.blackduckProjectVersion = blackduckProjectVersion;
    }
    
    /**
     * @deprecated This field is not used. Cleanup happens after scan completion.
     * Kept for backward compatibility.
     */
    @Deprecated
    public boolean isCleanupAfterEachScan() {
        return cleanupAfterEachScan;
    }
    
    /**
     * @deprecated This field is not used. Cleanup happens after scan completion.
     * Kept for backward compatibility.
     */
    @Deprecated
    public void setCleanupAfterEachScan(boolean cleanupAfterEachScan) {
        this.cleanupAfterEachScan = cleanupAfterEachScan;
    }
    
    public long getMaxWorkspaceSizeBytes() {
        return maxWorkspaceSizeBytes;
    }
    
    public void setMaxWorkspaceSizeBytes(long maxWorkspaceSizeBytes) {
        this.maxWorkspaceSizeBytes = maxWorkspaceSizeBytes;
    }
    
    // Note: executeScansInParallel removed - not applicable with single scan type per workflow
    
    public CloneStrategy getCloneStrategy() {
        return cloneStrategy;
    }
    
    public void setCloneStrategy(CloneStrategy cloneStrategy) {
        this.cloneStrategy = cloneStrategy;
    }
    
    public int getShallowCloneDepth() {
        return shallowCloneDepth;
    }
    
    public void setShallowCloneDepth(int shallowCloneDepth) {
        this.shallowCloneDepth = shallowCloneDepth;
    }
    
    public boolean isUseSparseCheckout() {
        return useSparseCheckout;
    }
    
    public void setUseSparseCheckout(boolean useSparseCheckout) {
        this.useSparseCheckout = useSparseCheckout;
    }
    
    public List<String> getSparseCheckoutPaths() {
        return sparseCheckoutPaths;
    }
    
    public void setSparseCheckoutPaths(List<String> sparseCheckoutPaths) {
        this.sparseCheckoutPaths = sparseCheckoutPaths;
    }
    
    public void addSparseCheckoutPath(String path) {
        this.sparseCheckoutPaths.add(path);
    }
    
    public StorageConfig getStorageConfig() {
        return storageConfig;
    }
    
    public void setStorageConfig(StorageConfig storageConfig) {
        this.storageConfig = storageConfig;
    }
    
    public Integer getScanTimeoutSeconds() {
        return scanTimeoutSeconds;
    }
    
    public void setScanTimeoutSeconds(Integer scanTimeoutSeconds) {
        this.scanTimeoutSeconds = scanTimeoutSeconds;
    }
    
    public Integer getWorkflowTimeoutSeconds() {
        return workflowTimeoutSeconds;
    }
    
    public void setWorkflowTimeoutSeconds(Integer workflowTimeoutSeconds) {
        this.workflowTimeoutSeconds = workflowTimeoutSeconds;
    }
    
    public String getTaskQueue() {
        return taskQueue;
    }
    
    public void setTaskQueue(String taskQueue) {
        this.taskQueue = taskQueue;
    }
    
}

