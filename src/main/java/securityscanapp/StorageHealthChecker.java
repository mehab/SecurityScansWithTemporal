package securityscanapp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for checking storage health and detecting storage failures
 */
public class StorageHealthChecker {
    
    /**
     * Check if storage at the given path is healthy and accessible
     * @param storagePath Path to check
     * @throws StorageFailureException if storage is unavailable
     */
    public static void checkStorageHealth(String storagePath) {
        try {
            Path path = Paths.get(storagePath);
            
            // Try to access parent directory if path doesn't exist
            if (!Files.exists(path)) {
                path = path.getParent();
                if (path == null) {
                    path = Paths.get("/");
                }
            }
            
            // Test write access (create temp file)
            Path testFile = path.resolve(".storage-health-check-" + System.currentTimeMillis());
            try {
                Files.createFile(testFile);
                Files.delete(testFile);
            } catch (Exception e) {
                // If we can't create/delete, storage might be read-only or failed
                if (isStorageFailure(e)) {
                    throw new StorageFailureException(
                        "Storage health check failed: cannot write to storage",
                        storagePath,
                        "Write test failed: " + e.getMessage(),
                        e
                    );
                }
            }
            
            // Test read access
            if (!Files.isReadable(path)) {
                throw new StorageFailureException(
                    "Storage health check failed: cannot read from storage",
                    storagePath,
                    "Read permission denied"
                );
            }
            
            // Test that path exists and is accessible
            if (!Files.exists(path)) {
                throw new StorageFailureException(
                    "Storage health check failed: path does not exist",
                    storagePath,
                    "Path not found"
                );
            }
            
        } catch (StorageFailureException e) {
            throw e;
        } catch (Exception e) {
            if (isStorageFailure(e)) {
                throw new StorageFailureException(
                    "Storage health check failed: " + e.getMessage(),
                    storagePath,
                    e.getClass().getSimpleName() + ": " + e.getMessage(),
                    e
                );
            }
            // Other exceptions are not storage failures, let them propagate
            throw new RuntimeException("Storage health check error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Determine if an exception indicates storage/PVC failure
     */
    public static boolean isStorageFailure(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        
        // Common storage failure indicators
        return lowerMessage.contains("no space left") ||
               lowerMessage.contains("read-only file system") ||
               lowerMessage.contains("input/output error") ||
               lowerMessage.contains("i/o error") ||
               lowerMessage.contains("device or resource busy") ||
               lowerMessage.contains("transport endpoint is not connected") ||
               lowerMessage.contains("stale file handle") ||
               lowerMessage.contains("connection refused") ||
               lowerMessage.contains("mount point") ||
               lowerMessage.contains("broken pipe") ||
               lowerMessage.contains("connection reset") ||
               (e instanceof java.nio.file.FileSystemException) ||
               (e instanceof java.io.IOException && 
                (lowerMessage.contains("broken pipe") || 
                 lowerMessage.contains("connection reset")));
    }
    
    /**
     * Check if a file path is accessible (storage is healthy)
     * Used before accessing repository or files
     */
    public static void verifyPathAccessible(String filePath) {
        try {
            Path path = Paths.get(filePath);
            
            // Check parent directory if file doesn't exist
            if (!Files.exists(path)) {
                Path parent = path.getParent();
                if (parent != null) {
                    checkStorageHealth(parent.toString());
                } else {
                    checkStorageHealth("/");
                }
            } else {
                // Check the path itself
                if (!Files.isReadable(path)) {
                    throw new StorageFailureException(
                        "Cannot read from path: " + filePath,
                        filePath,
                        "Read permission denied"
                    );
                }
            }
        } catch (StorageFailureException e) {
            throw e;
        } catch (Exception e) {
            if (isStorageFailure(e)) {
                throw new StorageFailureException(
                    "Path access check failed: " + e.getMessage(),
                    filePath,
                    e.getClass().getSimpleName() + ": " + e.getMessage(),
                    e
                );
            }
        }
    }
}

