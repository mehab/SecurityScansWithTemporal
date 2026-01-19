package securityscanapp;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for Gitleaks scanning operations
 * Supports both secrets scanning and file hash scanning
 */
@ActivityInterface
public interface GitleaksScanActivity {
    
    /**
     * Perform Gitleaks secrets scan
     * @param repoPath Path to the repository to scan
     * @param config Scan configuration
     * @return Scan result
     */
    @ActivityMethod
    ScanResult scanSecrets(String repoPath, ScanConfig config);
    
    /**
     * Perform Gitleaks file hash scan
     * @param repoPath Path to the repository to scan
     * @param config Scan configuration
     * @return Scan result
     */
    @ActivityMethod
    ScanResult scanFileHash(String repoPath, ScanConfig config);
}

