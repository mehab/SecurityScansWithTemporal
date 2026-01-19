package securityscanapp;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for repository management operations
 */
@ActivityInterface
public interface RepositoryActivity {
    
    /**
     * Clone a repository from SCM to the workspace
     * @param request Scan request containing repository information
     * @return Path to the cloned repository
     */
    @ActivityMethod
    String cloneRepository(ScanRequest request);
    
    /**
     * Clean up workspace directory to free space
     * @param workspacePath Path to workspace directory to clean
     * @return True if cleanup was successful
     */
    @ActivityMethod
    boolean cleanupWorkspace(String workspacePath);
    
    /**
     * Check available space in workspace
     * @param workspacePath Path to workspace directory
     * @return Available space in bytes
     */
    @ActivityMethod
    long getAvailableSpace(String workspacePath);
}

