package securityscanapp;

/**
 * Types of storage backends supported for storing scan results
 */
public enum StorageType {
    /**
     * Local filesystem storage (e.g., mounted NFS, PVC)
     * Results stored directly to filesystem path
     */
    LOCAL_FILESYSTEM("local-filesystem", "Local filesystem storage"),
    
    /**
     * Generic object storage (S3-compatible, MinIO, etc.)
     * Uses S3-compatible API
     */
    OBJECT_STORAGE("object-storage", "Object storage (S3-compatible)");
    
    private final String id;
    private final String description;
    
    StorageType(String id, String description) {
        this.id = id;
        this.description = description;
    }
    
    public String getId() {
        return id;
    }
    
    public String getDescription() {
        return description;
    }
}

