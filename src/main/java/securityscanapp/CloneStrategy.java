package securityscanapp;

/**
 * Git clone strategies for space-efficient repository cloning
 */
public enum CloneStrategy {
    /**
     * Full clone - clones entire repository history
     * Largest size, slowest, but complete history
     */
    FULL("full", "Full clone with complete history"),
    
    /**
     * Shallow clone - only specified depth of commits
     * Significantly smaller, faster, but limited history
     * Recommended for large repositories
     */
    SHALLOW("shallow", "Shallow clone with limited history"),
    
    /**
     * Single branch clone - only clone specific branch
     * Smaller than full, includes branch history
     */
    SINGLE_BRANCH("single-branch", "Clone only specified branch"),
    
    /**
     * Shallow single branch - combination of shallow and single branch
     * Smallest size, fastest, recommended for large repos
     */
    SHALLOW_SINGLE_BRANCH("shallow-single-branch", "Shallow clone of single branch");
    
    private final String id;
    private final String description;
    
    CloneStrategy(String id, String description) {
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

