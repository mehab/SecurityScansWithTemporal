package securityscanapp;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for storing scan results to external storage
 * Supports various storage backends (local filesystem, object storage, etc.)
 */
@ActivityInterface
public interface StorageActivity {
    
    /**
     * Store scan summary and results to external storage
     * @param summary Scan summary containing all results
     * @param config Storage configuration
     * @return Storage path where results were stored
     */
    @ActivityMethod
    String storeScanResults(ScanSummary summary, StorageConfig config);
    
    /**
     * Store individual scan report files to external storage
     * @param scanId Scan ID
     * @param reportPath Local path to report file
     * @param reportType Type of report (gitleaks, blackduck, etc.)
     * @param config Storage configuration
     * @return Storage path where report was stored
     */
    @ActivityMethod
    String storeReportFile(String scanId, String reportPath, String reportType, StorageConfig config);
}

