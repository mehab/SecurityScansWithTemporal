package securityscanapp;

/**
 * Exception thrown when persistent storage (PVC) fails or becomes unavailable
 * This indicates that workflows need to be restarted from the beginning
 * because the storage state is lost or corrupted
 */
public class StorageFailureException extends RuntimeException {
    private final String storagePath;
    private final String failureReason;
    
    public StorageFailureException(String message, String storagePath, String failureReason) {
        super(message);
        this.storagePath = storagePath;
        this.failureReason = failureReason;
    }
    
    public StorageFailureException(String message, String storagePath, String failureReason, Throwable cause) {
        super(message, cause);
        this.storagePath = storagePath;
        this.failureReason = failureReason;
    }
    
    public String getStoragePath() {
        return storagePath;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
}

