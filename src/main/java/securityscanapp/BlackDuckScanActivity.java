package securityscanapp;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for BlackDuck Detect scanning operations
 */
@ActivityInterface
public interface BlackDuckScanActivity {
    
    /**
     * Perform BlackDuck Detect signature scan
     * @param repoPath Path to the repository to scan
     * @param config Scan configuration
     * @return Scan result
     */
    @ActivityMethod
    ScanResult scanSignatures(String repoPath, ScanConfig config);
}

