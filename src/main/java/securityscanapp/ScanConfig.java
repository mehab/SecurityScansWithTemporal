package securityscanapp;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for security scans
 */
public class ScanConfig {
    private String gitUsername;
    private String gitPassword; // Should use secure credential management in production
    private String gitleaksConfigPath;
    private String blackduckApiToken;
    private String blackduckUrl;
    private String blackduckProjectName;
    private String blackduckProjectVersion;
    private boolean cleanupAfterEachScan;
    private long maxWorkspaceSizeBytes;
    private boolean executeScansInParallel; // Whether to run scans in parallel
    private CloneStrategy cloneStrategy; // Strategy for cloning large repos
    private int shallowCloneDepth; // Depth for shallow clone (1 = only latest commit)
    private boolean useSparseCheckout; // Use sparse checkout for large repos
    private List<String> sparseCheckoutPaths; // Paths to include in sparse checkout
    private StorageConfig storageConfig; // Configuration for storing results to external storage
    private Integer scanTimeoutSeconds; // Per-scan timeout in seconds (null = use default)
    private Integer workflowTimeoutSeconds; // Workflow execution timeout in seconds (null = no timeout)
    private String taskQueue; // Task queue name (null = auto-determined based on scan type)
    
    public ScanConfig() {
        this.cleanupAfterEachScan = true; // Default to cleanup for space efficiency
        this.maxWorkspaceSizeBytes = Shared.MAX_WORKSPACE_SIZE_BYTES;
        this.executeScansInParallel = false; // Default to sequential for space efficiency
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
    
    public String getGitleaksConfigPath() {
        return gitleaksConfigPath;
    }
    
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
    
    public boolean isCleanupAfterEachScan() {
        return cleanupAfterEachScan;
    }
    
    public void setCleanupAfterEachScan(boolean cleanupAfterEachScan) {
        this.cleanupAfterEachScan = cleanupAfterEachScan;
    }
    
    public long getMaxWorkspaceSizeBytes() {
        return maxWorkspaceSizeBytes;
    }
    
    public void setMaxWorkspaceSizeBytes(long maxWorkspaceSizeBytes) {
        this.maxWorkspaceSizeBytes = maxWorkspaceSizeBytes;
    }
    
    public boolean isExecuteScansInParallel() {
        return executeScansInParallel;
    }
    
    public void setExecuteScansInParallel(boolean executeScansInParallel) {
        this.executeScansInParallel = executeScansInParallel;
    }
    
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

