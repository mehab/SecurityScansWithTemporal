package securityscanapp;

/**
 * Priority levels for security scans
 * Determines which task queue the scan will use
 */
public enum ScanPriority {
    /**
     * Normal priority - uses default task queue
     */
    NORMAL,
    
    /**
     * High priority - uses priority task queue for faster processing
     */
    HIGH,
    
    /**
     * Low priority - can use default or long-running queue based on timeout
     */
    LOW
}
