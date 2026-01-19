package securityscanapp;

import java.util.List;
import java.util.ArrayList;

/**
 * Request object containing all information needed to perform security scans
 */
public class ScanRequest {
    private String scanId;
    private String repositoryUrl;
    private String branch;
    private String commitSha;
    private String workspacePath; // Unique workspace path for this scan
    private List<ScanType> scanTypes;
    private ScanConfig scanConfig;
    
    public ScanRequest() {
        this.scanTypes = new ArrayList<>();
    }
    
    public ScanRequest(String scanId, String repositoryUrl, String branch, String commitSha) {
        this.scanId = scanId;
        this.repositoryUrl = repositoryUrl;
        this.branch = branch;
        this.commitSha = commitSha;
        this.scanTypes = new ArrayList<>();
        this.workspacePath = Shared.WORKSPACE_BASE_DIR + "/" + scanId;
    }
    
    // Getters and Setters
    public String getScanId() {
        return scanId;
    }
    
    public void setScanId(String scanId) {
        this.scanId = scanId;
        if (this.workspacePath == null) {
            this.workspacePath = Shared.WORKSPACE_BASE_DIR + "/" + scanId;
        }
    }
    
    public String getRepositoryUrl() {
        return repositoryUrl;
    }
    
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
    
    public String getBranch() {
        return branch;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public String getCommitSha() {
        return commitSha;
    }
    
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
    
    public String getWorkspacePath() {
        return workspacePath;
    }
    
    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
    }
    
    public List<ScanType> getScanTypes() {
        return scanTypes;
    }
    
    public void setScanTypes(List<ScanType> scanTypes) {
        this.scanTypes = scanTypes;
    }
    
    public void addScanType(ScanType scanType) {
        this.scanTypes.add(scanType);
    }
    
    public ScanConfig getScanConfig() {
        return scanConfig;
    }
    
    public void setScanConfig(ScanConfig scanConfig) {
        this.scanConfig = scanConfig;
    }
}

