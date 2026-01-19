package securityscanapp;

import java.util.Map;
import java.util.HashMap;

/**
 * Result of a security scan execution
 */
public class ScanResult {
    private ScanType scanType;
    private boolean success;
    private String output;
    private String errorMessage;
    private Map<String, String> metadata;
    private long executionTimeMs;
    
    public ScanResult() {
        this.metadata = new HashMap<>();
    }
    
    public ScanResult(ScanType scanType, boolean success) {
        this.scanType = scanType;
        this.success = success;
        this.metadata = new HashMap<>();
    }
    
    // Getters and Setters
    public ScanType getScanType() {
        return scanType;
    }
    
    public void setScanType(ScanType scanType) {
        this.scanType = scanType;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getOutput() {
        return output;
    }
    
    public void setOutput(String output) {
        this.output = output;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }
    
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
    
    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }
}

