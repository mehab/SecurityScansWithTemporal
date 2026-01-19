package securityscanapp;

/**
 * Exception thrown when system resources are exhausted
 * This includes CPU throttling, memory pressure, or other resource constraints
 */
public class ResourceExhaustionException extends RuntimeException {
    private final String resourceType; // CPU, MEMORY, etc.
    private final String failureReason;
    
    public ResourceExhaustionException(String message, String resourceType, String failureReason) {
        super(message);
        this.resourceType = resourceType;
        this.failureReason = failureReason;
    }
    
    public ResourceExhaustionException(String message, String resourceType, String failureReason, Throwable cause) {
        super(message, cause);
        this.resourceType = resourceType;
        this.failureReason = failureReason;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
}

