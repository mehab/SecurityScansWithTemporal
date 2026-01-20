package securityscanapp;

/**
 * Shared constants and configuration for the security scanning application
 */
public interface Shared {
    // Task queue for security scanning workflows and activities
    static final String SECURITY_SCAN_TASK_QUEUE = "SECURITY_SCAN_TASK_QUEUE";
    
    // Base directory for workspace operations (should be on PVC)
    static final String WORKSPACE_BASE_DIR = "/workspace/security-scans";
    
    // Maximum workspace size in bytes (configurable, e.g., 10GB)
    static final long MAX_WORKSPACE_SIZE_BYTES = 10L * 1024 * 1024 * 1024;
    
    // CLI tool size estimates (for space calculations)
    static final long BLACKDUCK_DETECT_JAR_SIZE = 100L * 1024 * 1024; // ~100MB (downloaded on first run)
    static final long BLACKDUCK_DETECT_SCRIPT_SIZE = 10L * 1024; // ~10KB
    static final long TOTAL_CLI_TOOLS_SIZE = BLACKDUCK_DETECT_JAR_SIZE + 
                                               BLACKDUCK_DETECT_SCRIPT_SIZE; // ~110MB
    
    // Activity timeouts
    static final int CLONE_TIMEOUT_SECONDS = 600; // 10 minutes for large repos
    static final int SCAN_TIMEOUT_SECONDS = 1800; // 30 minutes for scans (default)
    
    // Task queue names for scan type-based routing (one queue per tool type)
    static final String TASK_QUEUE_BLACKDUCK = "SECURITY_SCAN_TASK_QUEUE_BLACKDUCK";
    
    // Default fallback queue (used if scan type is not recognized)
    static final String SECURITY_SCAN_TASK_QUEUE_DEFAULT = "SECURITY_SCAN_TASK_QUEUE";
    
    /**
     * Get task queue name for a specific scan type
     * Each scan type (tool type) has its own dedicated queue
     * 
     * Structure supports adding new scan types in the future
     */
    static String getTaskQueueForScanType(ScanType scanType) {
        if (scanType == null) {
            return SECURITY_SCAN_TASK_QUEUE_DEFAULT;
        }
        
        switch (scanType) {
            case BLACKDUCK_DETECT:
                return TASK_QUEUE_BLACKDUCK;
            default:
                return SECURITY_SCAN_TASK_QUEUE_DEFAULT;
        }
    }
}

