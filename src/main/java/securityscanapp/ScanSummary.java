package securityscanapp;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Summary of scan results for a security scan execution
 * 
 * Currently contains a single scan result (BlackDuck Detect).
 * The structure (List<ScanResult>) is maintained for future extensibility
 * to support multiple scan types per workflow execution.
 */
public class ScanSummary {
    private String scanId;
    private String appId; // Application ID
    private String component; // Component name
    private String buildId; // Build ID
    private ScanType toolType; // Tool type (scan type)
    private String repositoryUrl;
    private String commitSha;
    private boolean allScansSuccessful; // Success status (kept as "allScans" for backward compatibility, but represents single scan)
    private List<ScanResult> scanResults;
    private Map<String, Object> summary;
    private long totalExecutionTimeMs;
    
    public ScanSummary() {
        this.scanResults = new ArrayList<>();
        this.summary = new HashMap<>();
    }
    
    public ScanSummary(String scanId) {
        this.scanId = scanId;
        this.scanResults = new ArrayList<>();
        this.summary = new HashMap<>();
    }
    
    // Getters and Setters
    public String getScanId() {
        return scanId;
    }
    
    public void setScanId(String scanId) {
        this.scanId = scanId;
    }
    
    public String getRepositoryUrl() {
        return repositoryUrl;
    }
    
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
    
    public String getCommitSha() {
        return commitSha;
    }
    
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
    
    public boolean isAllScansSuccessful() {
        return allScansSuccessful;
    }
    
    public void setAllScansSuccessful(boolean allScansSuccessful) {
        this.allScansSuccessful = allScansSuccessful;
    }
    
    public List<ScanResult> getScanResults() {
        return scanResults;
    }
    
    public void setScanResults(List<ScanResult> scanResults) {
        this.scanResults = scanResults;
    }
    
    public void addScanResult(ScanResult result) {
        this.scanResults.add(result);
    }
    
    public Map<String, Object> getSummary() {
        return summary;
    }
    
    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }
    
    public long getTotalExecutionTimeMs() {
        return totalExecutionTimeMs;
    }
    
    public void setTotalExecutionTimeMs(long totalExecutionTimeMs) {
        this.totalExecutionTimeMs = totalExecutionTimeMs;
    }
    
    /**
     * Add metadata to the summary
     */
    public void addMetadata(String key, String value) {
        this.summary.put(key, value);
    }
    
    /**
     * Get metadata value
     */
    public Object getMetadata(String key) {
        return this.summary.get(key);
    }
    
    // Getters and Setters for new structure
    public String getAppId() {
        return appId;
    }
    
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    public String getComponent() {
        return component;
    }
    
    public void setComponent(String component) {
        this.component = component;
    }
    
    public String getBuildId() {
        return buildId;
    }
    
    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }
    
    public ScanType getToolType() {
        return toolType;
    }
    
    public void setToolType(ScanType toolType) {
        this.toolType = toolType;
    }
}

