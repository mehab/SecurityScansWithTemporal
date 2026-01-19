package securityscanapp;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Summary of all scan results for a complete security scan execution
 */
public class ScanSummary {
    private String scanId;
    private String repositoryUrl;
    private String commitSha;
    private boolean allScansSuccessful;
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
}

