package securityscanapp;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Implementation of repository management activities
 * Handles cloning repositories and workspace cleanup
 */
public class RepositoryActivityImpl implements RepositoryActivity {
    
    @Override
    public String cloneRepository(ScanRequest request) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        try {
            String workspacePath = request.getWorkspacePath();
            String repoPath = workspacePath + "/repo";
            
            // Check storage health first (will throw StorageFailureException if storage is down)
            checkStorageHealth(workspacePath);
            
            // Create workspace directory if it doesn't exist
            Path workspaceDir = Paths.get(workspacePath);
            try {
                Files.createDirectories(workspaceDir);
            } catch (Exception e) {
                if (isStorageFailure(e)) {
                    throw new StorageFailureException(
                        "Failed to create workspace directory: storage unavailable",
                        workspacePath,
                        e.getClass().getSimpleName() + ": " + e.getMessage(),
                        e
                    );
                }
                throw e;
            }
            
            // Send heartbeat to indicate progress
            context.heartbeat("Creating workspace directory: " + workspacePath);
            
            // Check available space before cloning (also checks storage health)
            long availableSpace = getAvailableSpace(workspacePath);
            ScanConfig config = request.getScanConfig();
            
            // Calculate minimum required space:
            // - Repository clone (estimated based on config)
            // - CLI tools (BlackDuck Detect JAR)
            // - Scan outputs (reports, logs)
            // - Temporary files during operations
            long estimatedRepoSize = estimateRepositorySize(request, config);
            long cliToolsSize = Shared.TOTAL_CLI_TOOLS_SIZE;
            long scanOutputsSize = 500L * 1024 * 1024; // ~500MB for scan outputs
            long tempFilesSize = 200L * 1024 * 1024; // ~200MB for temporary files
            long minRequiredSpace = estimatedRepoSize + cliToolsSize + scanOutputsSize + tempFilesSize;
            
            // Also ensure we have at least 25% of max workspace size free
            long maxWorkspaceSize = config != null ? config.getMaxWorkspaceSizeBytes() : Shared.MAX_WORKSPACE_SIZE_BYTES;
            long minFreeSpace = maxWorkspaceSize / 4; // 25% buffer
            minRequiredSpace = Math.max(minRequiredSpace, minFreeSpace);
            
            if (availableSpace < minRequiredSpace) {
                String errorMessage = String.format(
                    "Insufficient space available: %d MB (required: %d MB). " +
                    "Breakdown: Repo=%dMB, CLI Tools=%dMB, Outputs=%dMB, Temp=%dMB. " +
                    "Space may become available when other workflows complete.",
                    availableSpace / (1024 * 1024),
                    minRequiredSpace / (1024 * 1024),
                    estimatedRepoSize / (1024 * 1024),
                    cliToolsSize / (1024 * 1024),
                    scanOutputsSize / (1024 * 1024),
                    tempFilesSize / (1024 * 1024)
                );
                
                // Throw retryable exception - space may become available later
                // Temporal will retry based on retry configuration
                throw Activity.wrap(new InsufficientSpaceException(
                    errorMessage, availableSpace, minRequiredSpace
                ));
            }
            
            context.heartbeat("Available space: " + (availableSpace / (1024 * 1024)) + " MB");
            
            // Check if repository already exists (idempotency for retries)
            Path repoPathObj = Paths.get(repoPath);
            if (Files.exists(repoPathObj) && Files.isDirectory(repoPathObj)) {
                // Repository already exists - verify it's valid
                Path gitDir = repoPathObj.resolve(".git");
                if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
                    context.heartbeat("Repository already exists at: " + repoPath + " (from previous attempt or retry)");
                    
                    // Verify it's the correct repository by checking remote URL
                    if (isCorrectRepository(repoPath, request.getRepositoryUrl(), context)) {
                        context.heartbeat("Existing repository verified, skipping clone");
                        // Still need to checkout correct branch/commit if specified
                        if (request.getBranch() != null || request.getCommitSha() != null) {
                            checkoutRef(repoPath, request.getBranch(), request.getCommitSha(), context);
                        }
                        return repoPath;
                    } else {
                        context.heartbeat("Existing repository is different, removing and re-cloning");
                        // Remove existing repo and re-clone
                        deleteDirectory(repoPathObj);
                    }
                } else {
                    context.heartbeat("Directory exists but is not a valid git repository, removing");
                    deleteDirectory(repoPathObj);
                }
            }
            
            // Build git clone command with space-efficient options
            String[] cloneCommand = buildCloneCommand(request, repoPath);
            
            context.heartbeat("Cloning repository: " + request.getRepositoryUrl());
            
            // Execute git clone
            ProcessBuilder processBuilder = new ProcessBuilder(cloneCommand);
            processBuilder.directory(new File(workspacePath));
            processBuilder.redirectErrorStream(true);
            
            Process process;
            try {
                process = processBuilder.start();
            } catch (Exception e) {
                // Check if this is a storage failure (e.g., cannot create process in workspace)
                if (isStorageFailure(e)) {
                    throw new StorageFailureException(
                        "Failed to start git clone process: storage may be unavailable",
                        workspacePath,
                        "Process creation failed: " + e.getMessage(),
                        e
                    );
                }
                throw new RuntimeException("Failed to start git clone process: " + e.getMessage(), e);
            }
            
            // Monitor process and send heartbeats
            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Git clone process interrupted", e);
            }
            
            if (exitCode != 0) {
                // Check if exit code indicates storage issues
                // Git exit code 128 often indicates filesystem issues
                if (exitCode == 128) {
                    // Re-check storage health
                    try {
                        checkStorageHealth(workspacePath);
                    } catch (StorageFailureException storageEx) {
                        throw storageEx; // Re-throw storage failure
                    }
                }
                throw new RuntimeException("Git clone failed with exit code: " + exitCode);
            }
            
            context.heartbeat("Repository cloned successfully to: " + repoPath);
            
            // Checkout specific branch/commit if specified
            if (request.getBranch() != null || request.getCommitSha() != null) {
                checkoutRef(repoPath, request.getBranch(), request.getCommitSha(), context);
            }
            
            // Apply sparse checkout if configured (for large repos)
            // Reuse config variable from line 39
            if (config != null && config.isUseSparseCheckout() && !config.getSparseCheckoutPaths().isEmpty()) {
                setupSparseCheckout(repoPath, config.getSparseCheckoutPaths(), context);
            }
            
            // Clean up git history to save space (for shallow clones)
            if (config != null && (config.getCloneStrategy() == CloneStrategy.SHALLOW || 
                                   config.getCloneStrategy() == CloneStrategy.SHALLOW_SINGLE_BRANCH)) {
                cleanupGitHistory(repoPath, context);
            }
            
            // Report final repository size
            long repoSize = calculateDirectorySize(Paths.get(repoPath));
            context.heartbeat("Repository size: " + (repoSize / (1024 * 1024)) + " MB");
            
            return repoPath;
            
        } catch (Exception e) {
            throw Activity.wrap(new RuntimeException("Failed to clone repository: " + e.getMessage(), e));
        }
    }
    
    private String[] buildCloneCommand(ScanRequest request, String repoPath) {
        ScanConfig config = request.getScanConfig();
        CloneStrategy strategy = (config != null && config.getCloneStrategy() != null) 
            ? config.getCloneStrategy() 
            : CloneStrategy.SHALLOW; // Default to shallow for space efficiency
        
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("clone");
        
        // Apply space-efficient clone strategies
        switch (strategy) {
            case SHALLOW:
                int depth = (config != null && config.getShallowCloneDepth() > 0) 
                    ? config.getShallowCloneDepth() 
                    : 1;
                command.add("--depth");
                command.add(String.valueOf(depth));
                break;
            case SHALLOW_SINGLE_BRANCH:
                // Both shallow and single-branch options
                int shallowDepth = (config != null && config.getShallowCloneDepth() > 0) 
                    ? config.getShallowCloneDepth() 
                    : 1;
                command.add("--depth");
                command.add(String.valueOf(shallowDepth));
                command.add("--single-branch");
                if (request.getBranch() != null) {
                    command.add("--branch");
                    command.add(request.getBranch());
                }
                break;
            case SINGLE_BRANCH:
                command.add("--single-branch");
                if (request.getBranch() != null) {
                    command.add("--branch");
                    command.add(request.getBranch());
                }
                break;
            case FULL:
                // No special options for full clone
                break;
        }
        
        // Add authentication if provided
        String repoUrl = request.getRepositoryUrl();
        if (config != null && config.getGitUsername() != null && config.getGitPassword() != null) {
            // For production, use credential helper or environment variables
            // This is a simplified example
            repoUrl = repoUrl.replace("https://", 
                "https://" + config.getGitUsername() + ":" + config.getGitPassword() + "@");
        }
        
        command.add(repoUrl);
        command.add(repoPath);
        
        return command.toArray(new String[0]);
    }
    
    private void checkoutRef(String repoPath, String branch, String commitSha, 
                            ActivityExecutionContext context) throws IOException, InterruptedException {
        context.heartbeat("Checking out reference");
        
        String ref = commitSha != null ? commitSha : branch;
        if (ref == null) return;
        
        ProcessBuilder processBuilder = new ProcessBuilder("git", "checkout", ref);
        processBuilder.directory(new File(repoPath));
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("Git checkout failed with exit code: " + exitCode);
        }
        
        context.heartbeat("Checked out: " + ref);
    }
    
    @Override
    public boolean cleanupWorkspace(String workspacePath) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        try {
            context.heartbeat("Starting workspace cleanup: " + workspacePath);
            
            Path path = Paths.get(workspacePath);
            if (!Files.exists(path)) {
                context.heartbeat("Workspace does not exist, nothing to clean");
                return true;
            }
            
            // Delete directory and all contents
            deleteDirectory(path);
            
            context.heartbeat("Workspace cleaned successfully");
            return true;
            
        } catch (Exception e) {
            throw Activity.wrap(new RuntimeException("Failed to cleanup workspace: " + e.getMessage(), e));
        }
    }
    
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> paths = Files.walk(path)) {
                paths.sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                     .forEach(p -> {
                         try {
                             Files.delete(p);
                         } catch (IOException e) {
                             // Log but continue
                             System.err.println("Failed to delete: " + p + " - " + e.getMessage());
                         }
                     });
            }
        }
    }
    
    @Override
    public long getAvailableSpace(String workspacePath) {
        try {
            // Check if storage is accessible (health check)
            checkStorageHealth(workspacePath);
            
            Path path = Paths.get(workspacePath);
            if (!Files.exists(path)) {
                // Return space for parent directory
                path = path.getParent();
                if (path == null) {
                    path = Paths.get("/");
                }
            }
            
            File file = path.toFile();
            return file.getFreeSpace();
            
        } catch (StorageFailureException e) {
            // Re-throw storage failures as-is (not wrapped)
            throw e;
        } catch (Exception e) {
            // Check if underlying exception indicates storage failure
            if (isStorageFailure(e)) {
                throw new StorageFailureException(
                    "Storage failure detected: " + e.getMessage(),
                    workspacePath,
                    e.getClass().getSimpleName() + ": " + e.getMessage(),
                    e
                );
            }
            throw Activity.wrap(new RuntimeException("Failed to get available space: " + e.getMessage(), e));
        }
    }
    
    /**
     * Check if storage is healthy and accessible
     * Throws StorageFailureException if storage is unavailable
     */
    private void checkStorageHealth(String workspacePath) {
        StorageHealthChecker.checkStorageHealth(workspacePath);
    }
    
    /**
     * Determine if an exception indicates storage/PVC failure
     */
    private boolean isStorageFailure(Exception e) {
        return StorageHealthChecker.isStorageFailure(e);
    }
    
    /**
     * Setup sparse checkout to only checkout specific paths
     * Useful for large repositories where only certain directories need scanning
     */
    private void setupSparseCheckout(String repoPath, List<String> paths, 
                                      ActivityExecutionContext context) throws IOException, InterruptedException {
        context.heartbeat("Setting up sparse checkout");
        
        // Enable sparse checkout
        ProcessBuilder enableProcess = new ProcessBuilder("git", "config", "core.sparseCheckout", "true");
        enableProcess.directory(new File(repoPath));
        enableProcess.redirectErrorStream(true);
        int exitCode = enableProcess.start().waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Failed to enable sparse checkout");
        }
        
        // Write sparse checkout paths
        Path sparseCheckoutFile = Paths.get(repoPath, ".git", "info", "sparse-checkout");
        Files.createDirectories(sparseCheckoutFile.getParent());
        StringBuilder content = new StringBuilder();
        for (String path : paths) {
            content.append(path).append("\n");
        }
        Files.write(sparseCheckoutFile, content.toString().getBytes());
        
        // Apply sparse checkout
        ProcessBuilder checkoutProcess = new ProcessBuilder("git", "read-tree", "-mu", "HEAD");
        checkoutProcess.directory(new File(repoPath));
        checkoutProcess.redirectErrorStream(true);
        exitCode = checkoutProcess.start().waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Failed to apply sparse checkout");
        }
        
        context.heartbeat("Sparse checkout configured for paths: " + paths);
    }
    
    /**
     * Clean up git history to save space after shallow clone
     * Removes unnecessary git objects and packs
     */
    private void cleanupGitHistory(String repoPath, ActivityExecutionContext context) 
            throws IOException, InterruptedException {
        context.heartbeat("Cleaning up git history to save space");
        
        // Run git gc to clean up unnecessary objects
        ProcessBuilder gcProcess = new ProcessBuilder("git", "gc", "--aggressive", "--prune=now");
        gcProcess.directory(new File(repoPath));
        gcProcess.redirectErrorStream(true);
        int exitCode = gcProcess.start().waitFor();
        
        if (exitCode != 0) {
            // Non-fatal, just log
            context.heartbeat("Git gc completed with warnings (non-fatal)");
        } else {
            context.heartbeat("Git history cleanup completed");
        }
    }
    
    /**
     * Verify if existing repository matches the requested repository URL
     * Used for idempotency checks during activity retries
     */
    private boolean isCorrectRepository(String repoPath, String expectedUrl, 
                                       ActivityExecutionContext context) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("git", "remote", "get-url", "origin");
            processBuilder.directory(new File(repoPath));
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line.trim());
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return false;
            }
            
            String actualUrl = output.toString();
            // Normalize URLs for comparison (remove auth tokens, normalize format)
            String normalizedExpected = normalizeGitUrl(expectedUrl);
            String normalizedActual = normalizeGitUrl(actualUrl);
            
            boolean matches = normalizedExpected.equals(normalizedActual);
            if (!matches) {
                context.heartbeat("Repository URL mismatch. Expected: " + normalizedExpected + 
                                 ", Found: " + normalizedActual);
            }
            
            return matches;
            
        } catch (Exception e) {
            context.heartbeat("Failed to verify repository URL: " + e.getMessage());
            return false; // If we can't verify, assume it's wrong and re-clone
        }
    }
    
    /**
     * Normalize git URL for comparison (remove credentials, normalize format)
     */
    private String normalizeGitUrl(String url) {
        if (url == null) return "";
        
        // Remove credentials from URL (e.g., https://user:pass@host/path -> https://host/path)
        String normalized = url.replaceAll("://[^@]+@", "://");
        
        // Remove .git suffix for comparison
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        
        // Normalize trailing slashes
        normalized = normalized.replaceAll("/+$", "");
        
        return normalized;
    }
    
    /**
     * Calculate total size of a directory
     */
    private long calculateDirectorySize(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0;
        }
        
        long size = 0;
        try (Stream<Path> paths = Files.walk(directory)) {
            size = paths
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                    return 0;
                }
            })
            .sum();
        }
        return size;
    }
    
    /**
     * Estimate repository size based on clone strategy
     */
    private long estimateRepositorySize(ScanRequest request, ScanConfig config) {
        // Base estimate for a typical repository
        long baseRepoSize = 4L * 1024 * 1024 * 1024; // 4GB default
        
        if (config == null) {
            return baseRepoSize;
        }
        
        CloneStrategy strategy = config.getCloneStrategy();
        boolean useSparse = config.isUseSparseCheckout() && !config.getSparseCheckoutPaths().isEmpty();
        
        // Adjust estimate based on clone strategy
        switch (strategy) {
            case FULL:
                // Full clone: repo + history
                return baseRepoSize + (2L * 1024 * 1024 * 1024); // +2GB for history
                
            case SHALLOW:
            case SHALLOW_SINGLE_BRANCH:
                // Shallow clone: repo + minimal history
                if (useSparse) {
                    // Sparse checkout: only selected paths
                    return baseRepoSize / 2; // Assume 50% reduction
                }
                return baseRepoSize + (100L * 1024 * 1024); // +100MB for minimal history
                
            case SINGLE_BRANCH:
                // Single branch: repo + branch history
                return baseRepoSize + (500L * 1024 * 1024); // +500MB for branch history
                
            default:
                return baseRepoSize;
        }
    }
}

