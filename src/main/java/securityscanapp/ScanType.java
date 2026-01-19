package securityscanapp;

/**
 * Enumeration of supported security scan types
 */
public enum ScanType {
    GITLEAKS_SECRETS("gitleaks-secrets", "Gitleaks secrets scanning"),
    GITLEAKS_FILE_HASH("gitleaks-file-hash", "Gitleaks file hash scanning"),
    BLACKDUCK_DETECT("blackduck-detect", "BlackDuck Detect signature scanning");
    
    private final String id;
    private final String description;
    
    ScanType(String id, String description) {
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

