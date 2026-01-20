package securityscanapp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for checking deployment health on OpenShift
 * Verifies that the pod can access required resources
 */
public class DeploymentHealthChecker {
    
    /**
     * Verify that the deployment is healthy and can access required resources
     * This should be called at worker startup
     * 
     * @throws DeploymentFailureException if deployment is unhealthy
     */
    public static void verifyDeploymentHealth() {
        // Check storage mount
        verifyStorageMount();
        
        // Check CLI tools availability
        verifyCLITools();
        
        // Check workspace directory
        verifyWorkspaceDirectory();
    }
    
    /**
     * Verify that storage (PVC) is mounted and accessible
     */
    private static void verifyStorageMount() {
        String workspacePath = Shared.WORKSPACE_BASE_DIR;
        
        try {
            Path path = Paths.get(workspacePath);
            
            // Check if path exists (mount point)
            if (!Files.exists(path)) {
                throw new DeploymentFailureException(
                    "Storage mount point does not exist: " + workspacePath,
                    "Storage mount failure",
                    "PVC may not be mounted or mount path is incorrect"
                );
            }
            
            // Check if it's a directory
            if (!Files.isDirectory(path)) {
                throw new DeploymentFailureException(
                    "Storage mount point is not a directory: " + workspacePath,
                    "Storage mount failure",
                    "Mount point exists but is not a directory"
                );
            }
            
            // Check read/write permissions
            if (!Files.isReadable(path)) {
                throw new DeploymentFailureException(
                    "Storage mount point is not readable: " + workspacePath,
                    "Storage mount failure",
                    "Read permission denied - check SCC and permissions"
                );
            }
            
            if (!Files.isWritable(path)) {
                throw new DeploymentFailureException(
                    "Storage mount point is not writable: " + workspacePath,
                    "Storage mount failure",
                    "Write permission denied - check SCC and permissions"
                );
            }
            
            // Test write access
            Path testFile = path.resolve(".deployment-health-check-" + System.currentTimeMillis());
            try {
                Files.createFile(testFile);
                Files.delete(testFile);
            } catch (Exception e) {
                throw new DeploymentFailureException(
                    "Cannot write to storage mount point: " + workspacePath,
                    "Storage mount failure",
                    "Write test failed: " + e.getMessage(),
                    e
                );
            }
            
        } catch (DeploymentFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentFailureException(
                "Storage mount verification failed: " + e.getMessage(),
                "Storage mount failure",
                e.getClass().getSimpleName() + ": " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Verify that required CLI tools are available
     */
    private static void verifyCLITools() {
        // Check Git
        if (!isCommandAvailable("git")) {
            throw new DeploymentFailureException(
                "Git command is not available",
                "CLI tool missing",
                "Git is required for repository cloning"
            );
        }
        
        // Note: Gitleaks has been removed from the application
        // Future scan types can be checked here
        
        // Check BlackDuck Detect script
        Path detectScript = Paths.get("/usr/local/bin/detect.sh");
        if (!Files.exists(detectScript) || !Files.isExecutable(detectScript)) {
            throw new DeploymentFailureException(
                "BlackDuck Detect script is not available or not executable",
                "CLI tool missing",
                "Detect script should be at /usr/local/bin/detect.sh"
            );
        }
    }
    
    /**
     * Verify workspace directory can be created/accessed
     */
    private static void verifyWorkspaceDirectory() {
        String workspacePath = Shared.WORKSPACE_BASE_DIR;
        
        try {
            Path path = Paths.get(workspacePath);
            
            // Try to create directory if it doesn't exist
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            
            // Verify we can write to it
            Path testFile = path.resolve(".workspace-test-" + System.currentTimeMillis());
            Files.createFile(testFile);
            Files.delete(testFile);
            
        } catch (Exception e) {
            throw new DeploymentFailureException(
                "Cannot access workspace directory: " + workspacePath,
                "Workspace access failure",
                e.getClass().getSimpleName() + ": " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Check if a command is available in PATH
     */
    private static boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("which", command);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}

