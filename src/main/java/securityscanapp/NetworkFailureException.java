package securityscanapp;

/**
 * Exception thrown when network connectivity fails
 * This includes Temporal service unavailability, DNS failures, network partitions
 */
public class NetworkFailureException extends RuntimeException {
    private final String serviceAddress;
    private final String failureReason;
    
    public NetworkFailureException(String message, String serviceAddress, String failureReason) {
        super(message);
        this.serviceAddress = serviceAddress;
        this.failureReason = failureReason;
    }
    
    public NetworkFailureException(String message, String serviceAddress, String failureReason, Throwable cause) {
        super(message, cause);
        this.serviceAddress = serviceAddress;
        this.failureReason = failureReason;
    }
    
    public String getServiceAddress() {
        return serviceAddress;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
}

