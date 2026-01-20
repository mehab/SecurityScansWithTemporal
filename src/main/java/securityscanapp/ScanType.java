package securityscanapp;

/**
 * Enumeration of supported security scan types
 * 
 * Structure is designed to support multiple scan types.
 * Currently only BlackDuck Detect is implemented.
 */
public enum ScanType {
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

