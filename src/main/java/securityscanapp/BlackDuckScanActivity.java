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
     * 
     * This method:
     * 1. Determines the appropriate BlackDuck Hub URL
     * 2. Executes the detect shell script with proper parameters
     * 3. Handles rapid scan vs regular scan results
     * 
     * @param repoPath Path to the cloned repository to scan
     * @param request Scan request containing BlackDuck configuration
     * @return Scan result
     */
    @ActivityMethod
    ScanResult scanSignatures(String repoPath, ScanRequest request);
}

