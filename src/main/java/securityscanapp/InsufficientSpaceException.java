package securityscanapp;

/**
 * Exception thrown when insufficient space is available for operations
 * This is a retryable exception - space may become available later
 */
public class InsufficientSpaceException extends RuntimeException {
    private final long availableSpace;
    private final long requiredSpace;
    
    public InsufficientSpaceException(String message, long availableSpace, long requiredSpace) {
        super(message);
        this.availableSpace = availableSpace;
        this.requiredSpace = requiredSpace;
    }
    
    public long getAvailableSpace() {
        return availableSpace;
    }
    
    public long getRequiredSpace() {
        return requiredSpace;
    }
}

