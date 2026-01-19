package securityscanapp;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Implementation of storage activities
 * Supports local filesystem and object storage backends
 */
public class StorageActivityImpl implements StorageActivity {
    
    @Override
    public String storeScanResults(ScanSummary summary, StorageConfig config) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        try {
            context.heartbeat("Storing scan results to storage");
            
            if (config == null) {
                throw new RuntimeException("Storage configuration is missing");
            }
            
            String storagePath;
            
            switch (config.getStorageType()) {
                case LOCAL_FILESYSTEM:
                    storagePath = storeToLocalFilesystem(summary, config, context);
                    break;
                    
                case OBJECT_STORAGE:
                    storagePath = storeToObjectStorage(summary, config, context);
                    break;
                    
                default:
                    throw new RuntimeException("Unsupported storage type: " + config.getStorageType());
            }
            
            context.heartbeat("Scan summary stored to: " + storagePath);
            return storagePath;
            
        } catch (Exception e) {
            throw Activity.wrap(new RuntimeException("Failed to store scan results: " + e.getMessage(), e));
        }
    }
    
    @Override
    public String storeReportFile(String scanId, String reportPath, String reportType, StorageConfig config) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        try {
            context.heartbeat("Storing report file: " + reportType);
            
            if (config == null) {
                throw new RuntimeException("Storage configuration is missing");
            }
            
            Path localReportPath = Paths.get(reportPath);
            if (!Files.exists(localReportPath)) {
                context.heartbeat("Report file does not exist, skipping: " + reportPath);
                return null;
            }
            
            String storagePath;
            
            switch (config.getStorageType()) {
                case LOCAL_FILESYSTEM:
                    storagePath = storeFileToLocalFilesystem(scanId, reportPath, reportType, config, context);
                    break;
                    
                case OBJECT_STORAGE:
                    storagePath = storeFileToObjectStorage(scanId, reportPath, reportType, config, context);
                    break;
                    
                default:
                    throw new RuntimeException("Unsupported storage type: " + config.getStorageType());
            }
            
            context.heartbeat("Report file stored to: " + storagePath);
            return storagePath;
            
        } catch (Exception e) {
            throw Activity.wrap(new RuntimeException("Failed to store report file: " + e.getMessage(), e));
        }
    }
    
    /**
     * Store scan summary to local filesystem
     */
    private String storeToLocalFilesystem(ScanSummary summary, StorageConfig config, 
                                         ActivityExecutionContext context) throws IOException {
        String basePath = config.getStorageBasePath();
        if (basePath == null || basePath.isEmpty()) {
            basePath = "/mnt/storage/scan-results";
        }
        
        // Build storage path
        String storageDir = basePath + "/" + summary.getScanId();
        Path storagePath = Paths.get(storageDir);
        Files.createDirectories(storagePath);
        
        // Write summary JSON
        String summaryFile = storageDir + "/summary.json";
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonContent = gson.toJson(summary);
        
        try (FileWriter writer = new FileWriter(summaryFile)) {
            writer.write(jsonContent);
        }
        
        return summaryFile;
    }
    
    /**
     * Store scan summary to object storage (S3-compatible)
     */
    private String storeToObjectStorage(ScanSummary summary, StorageConfig config,
                                       ActivityExecutionContext context) throws Exception {
        // For object storage, you would implement S3-compatible API calls here
        // This is a placeholder - implement based on your object storage solution
        // (MinIO, Ceph, S3-compatible APIs, etc.)
        
        throw new UnsupportedOperationException(
            "Object storage implementation requires S3-compatible client library. " +
            "Please implement based on your object storage solution (MinIO, Ceph, etc.)"
        );
    }
    
    /**
     * Store report file to local filesystem
     */
    private String storeFileToLocalFilesystem(String scanId, String reportPath, String reportType,
                                             StorageConfig config, ActivityExecutionContext context) 
                                             throws IOException {
        String basePath = config.getStorageBasePath();
        if (basePath == null || basePath.isEmpty()) {
            basePath = "/mnt/storage/scan-results";
        }
        
        // Build storage path
        String storageDir = basePath + "/" + scanId;
        Path storagePath = Paths.get(storageDir);
        Files.createDirectories(storagePath);
        
        // Determine file extension
        String extension = getFileExtension(reportPath);
        String targetFileName = reportType + extension;
        String targetPath = storageDir + "/" + targetFileName;
        
        // Copy file to storage
        Path sourcePath = Paths.get(reportPath);
        Path targetFilePath = Paths.get(targetPath);
        Files.copy(sourcePath, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
        
        return targetPath;
    }
    
    /**
     * Store report file to object storage
     */
    private String storeFileToObjectStorage(String scanId, String reportPath, String reportType,
                                           StorageConfig config, ActivityExecutionContext context) 
                                           throws Exception {
        // For object storage, you would implement S3-compatible API calls here
        // This is a placeholder - implement based on your object storage solution
        
        throw new UnsupportedOperationException(
            "Object storage implementation requires S3-compatible client library. " +
            "Please implement based on your object storage solution (MinIO, Ceph, etc.)"
        );
    }
    
    private String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filePath.substring(lastDot);
    }
}
