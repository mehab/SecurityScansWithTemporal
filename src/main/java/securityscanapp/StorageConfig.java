package securityscanapp;

/**
 * Configuration for external storage
 * Supports various storage backends: local filesystem, object storage, etc.
 */
public class StorageConfig {
    private StorageType storageType; // Type of storage backend
    private String storageBasePath; // Base path for storage (e.g., "/mnt/storage/scan-results" or "bucket-name/scan-results")
    private String storageEndpoint; // Optional: endpoint URL for object storage
    private String accessKeyId; // Optional: access key for object storage
    private String secretAccessKey; // Optional: secret key for object storage
    private boolean storeSummaryJson; // Store ScanSummary as JSON
    private boolean storeReportFiles; // Store individual report files
    
    public StorageConfig() {
        this.storageType = StorageType.LOCAL_FILESYSTEM; // Default to local filesystem
        this.storeSummaryJson = true;
        this.storeReportFiles = true;
    }
    
    // Getters and Setters
    public StorageType getStorageType() {
        return storageType;
    }
    
    public void setStorageType(StorageType storageType) {
        this.storageType = storageType;
    }
    
    public String getStorageBasePath() {
        return storageBasePath;
    }
    
    public void setStorageBasePath(String storageBasePath) {
        this.storageBasePath = storageBasePath;
    }
    
    public String getStorageEndpoint() {
        return storageEndpoint;
    }
    
    public void setStorageEndpoint(String storageEndpoint) {
        this.storageEndpoint = storageEndpoint;
    }
    
    public String getAccessKeyId() {
        return accessKeyId;
    }
    
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }
    
    public String getSecretAccessKey() {
        return secretAccessKey;
    }
    
    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }
    
    public boolean isStoreSummaryJson() {
        return storeSummaryJson;
    }
    
    public void setStoreSummaryJson(boolean storeSummaryJson) {
        this.storeSummaryJson = storeSummaryJson;
    }
    
    public boolean isStoreReportFiles() {
        return storeReportFiles;
    }
    
    public void setStoreReportFiles(boolean storeReportFiles) {
        this.storeReportFiles = storeReportFiles;
    }
}

