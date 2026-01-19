package securityscanapp;

/**
 * Exception thrown when deployment-level failures are detected
 * This includes storage mount failures, missing CLI tools, permission issues
 * These failures occur at pod startup and prevent activities from executing
 */
public class DeploymentFailureException extends RuntimeException {
    private final String failureType; // e.g., "Storage mount failure", "CLI tool missing"
    private final String failureReason;
    
    public DeploymentFailureException(String message, String failureType, String failureReason) {
        super(message);
        this.failureType = failureType;
        this.failureReason = failureReason;
    }
    
    public DeploymentFailureException(String message, String failureType, String failureReason, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
        this.failureReason = failureReason;
    }
    
    public String getFailureType() {
        return failureType;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
}

