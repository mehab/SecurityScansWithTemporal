package securityscanapp;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Implementation of the security scanning workflow
 * Orchestrates repository cloning, BlackDuck Detect scan, and cleanup
 * Designed for space-efficient execution on Kubernetes with limited PVC storage
 * 
 * Each workflow execution handles a single BlackDuck Detect scan.
 * The structure is designed to support additional scan types in the future.
 */
public class SecurityScanWorkflowImpl implements SecurityScanWorkflow {
    
    // Retry options for activities (general)
    private final RetryOptions retryOptions = RetryOptions.newBuilder()
        .setInitialInterval(Duration.ofSeconds(5))
        .setMaximumInterval(Duration.ofSeconds(60))
        .setBackoffCoefficient(2.0)
        .setMaximumAttempts(3)
        .build();
    
    // Retry options for space-related operations
    // Longer intervals and more attempts because space may become available
    // when other workflows complete and clean up
    private final RetryOptions spaceRetryOptions = RetryOptions.newBuilder()
        .setInitialInterval(Duration.ofMinutes(1))  // Wait 1 minute initially
        .setMaximumInterval(Duration.ofMinutes(10)) // Up to 10 minutes between retries
        .setBackoffCoefficient(1.5)  // Gradual backoff
        .setMaximumAttempts(10)  // More attempts for space issues
        .build();
    
    // Activity options for repository operations
    // Uses spaceRetryOptions to handle space-related failures with longer retry intervals
    private final ActivityOptions repositoryActivityOptions = ActivityOptions.newBuilder()
        .setRetryOptions(spaceRetryOptions)  // Longer retries for space issues
        .setStartToCloseTimeout(Duration.ofSeconds(Shared.CLONE_TIMEOUT_SECONDS))
        .setScheduleToCloseTimeout(Duration.ofHours(2))  // Allow up to 2 hours for retries
        .setHeartbeatTimeout(Duration.ofSeconds(30))
        .build();
    
    // Base activity options for scanning operations (can be overridden per scan)
    private ActivityOptions createScanActivityOptions(int timeoutSeconds) {
        return ActivityOptions.newBuilder()
            .setRetryOptions(retryOptions)
            .setStartToCloseTimeout(Duration.ofSeconds(timeoutSeconds))
            .setScheduleToCloseTimeout(Duration.ofSeconds(timeoutSeconds + 120))
            .setHeartbeatTimeout(Duration.ofSeconds(60))
            .build();
    }
    
    private final ActivityOptions defaultScanActivityOptions = 
        createScanActivityOptions(Shared.SCAN_TIMEOUT_SECONDS);

    // Activity stubs
    private final RepositoryActivity repositoryActivity = 
        Workflow.newActivityStub(RepositoryActivity.class, repositoryActivityOptions);
    
    // Activity stubs with default options (can be overridden per request)
    private final BlackDuckScanActivity blackduckActivity = 
        Workflow.newActivityStub(BlackDuckScanActivity.class, defaultScanActivityOptions);
    
    private final StorageActivity storageActivity = 
        Workflow.newActivityStub(StorageActivity.class, defaultScanActivityOptions);
    
    // Store original request for querying (used by WorkflowRestartClient)
    private ScanRequest originalRequest;
    
    @Override
    public ScanSummary executeScans(ScanRequest request) {
        // Store original request for querying
        this.originalRequest = request;
        
        long workflowStartTime = System.currentTimeMillis();
        
        ScanSummary summary = new ScanSummary(request.getScanId());
        summary.setRepositoryUrl(request.getRepositoryUrl());
        summary.setCommitSha(request.getCommitSha());
        
        // Set new structure fields if available
        if (request.getAppId() != null) {
            summary.setAppId(request.getAppId());
            summary.setComponent(request.getComponent());
            summary.setBuildId(request.getBuildId());
            summary.setToolType(request.getToolType());
        }
        
        ScanConfig config = request.getScanConfig();
        
        // Apply workflow-level timeout if configured
        if (config != null && config.getWorkflowTimeoutSeconds() != null) {
            // Use Workflow.sleep() with timeout check pattern
            // Note: Temporal workflows have built-in execution timeout via WorkflowOptions
            // This is set at workflow start, but we can track it here
            summary.addMetadata("workflowTimeoutSeconds", String.valueOf(config.getWorkflowTimeoutSeconds()));
        }
        
        String repoPath = null;
        
        try {
            // Step 1: Clone repository
            // Repository is cloned to shared storage (NFS/PVC RWX)
            // It persists across pod failures and is available for activity retries
            // Cleanup only happens after all activities complete successfully
            repoPath = repositoryActivity.cloneRepository(request);
            
            // Step 2: Execute BlackDuck Detect scan
            // Each workflow execution handles a single scan
            ScanType toolType = request.getToolType();
            if (toolType == null) {
                throw new IllegalArgumentException("Tool type (scan type) must be specified in ScanRequest");
            }
            
            // Execute scan
            ScanResult scanResult = executeSingleScan(toolType, repoPath, request);
            summary.addScanResult(scanResult);
            
            // Step 3: Determine overall success
            boolean allSuccessful = scanResult.isSuccess();
            summary.setAllScansSuccessful(allSuccessful);
            
            // Step 4: Final cleanup
            // Repository is cleaned up ONLY after scan completes successfully
            // If scan fails, cleanup is deferred (repository remains for investigation/retry)
            // During activity retries (pod failures), repository remains on shared storage
            // This ensures retries can access the same cloned repository
            if (allSuccessful && repoPath != null && request.getWorkspacePath() != null) {
                repositoryActivity.cleanupWorkspace(request.getWorkspacePath());
            } else if (!allSuccessful) {
                // If scan failed, keep repository for investigation
                // Repository will be cleaned up manually or by a separate cleanup process
                summary.addMetadata("cleanupDeferred", "Repository retained due to scan failure");
            }
            
            long totalExecutionTime = System.currentTimeMillis() - workflowStartTime;
            summary.setTotalExecutionTimeMs(totalExecutionTime);
            
            // Step 5: Store results to external storage if configured
            if (config != null && config.getStorageConfig() != null) {
                try {
                    String storagePath = storageActivity.storeScanResults(summary, config.getStorageConfig());
                    summary.addMetadata("storagePath", storagePath);
                    
                    // Store individual report files if configured
                    // Currently only BlackDuck reports are stored
                    if (config.getStorageConfig().isStoreReportFiles()) {
                        String reportPath = request.getWorkspacePath() + "/blackduck-output";
                        storageActivity.storeReportFile(
                            request.getScanId(),
                            reportPath,
                            "blackduck-detect",
                            config.getStorageConfig()
                        );
                    }
                } catch (Exception e) {
                    // Non-fatal: log but don't fail workflow
                    summary.addMetadata("storageError", "Failed to store results: " + e.getMessage());
                }
            }
            
            return summary;
            
        } catch (StorageFailureException e) {
            // Storage failure - workflow must be restarted from beginning
            // Don't attempt cleanup (storage is unavailable)
            // Mark summary with storage failure indicator
            summary.setAllScansSuccessful(false);
            summary.addMetadata("storageFailure", "true");
            summary.addMetadata("storageFailurePath", e.getStoragePath());
            summary.addMetadata("storageFailureReason", e.getFailureReason());
            summary.addMetadata("workflowRestartRequired", "true");
            summary.addScanResult(createErrorResult(
                "Storage failure detected: " + e.getMessage() + 
                ". Workflow must be restarted from beginning when storage is restored."
            ));
            
            long totalExecutionTime = System.currentTimeMillis() - workflowStartTime;
            summary.setTotalExecutionTimeMs(totalExecutionTime);
            
            // Throw StorageFailureException - this will fail the workflow
            // External system should monitor for this and restart workflows
            throw e;
            
        } catch (NetworkFailureException e) {
            // Network failure - may be transient, but workflow should fail
            // Temporal will handle retries, but workflow should be aware
            summary.setAllScansSuccessful(false);
            summary.addMetadata("networkFailure", "true");
            summary.addMetadata("networkFailureService", e.getServiceAddress());
            summary.addMetadata("networkFailureReason", e.getFailureReason());
            summary.addScanResult(createErrorResult(
                "Network failure detected: " + e.getMessage() + 
                ". Workflow may need to be restarted if network is not restored."
            ));
            
            long totalExecutionTime = System.currentTimeMillis() - workflowStartTime;
            summary.setTotalExecutionTimeMs(totalExecutionTime);
            
            // Throw NetworkFailureException - workflow fails
            // Temporal may retry, but if network is down, workflow will fail
            throw e;
            
        } catch (ResourceExhaustionException e) {
            // Resource exhaustion - workflow should fail
            // May need to restart with lower resource requirements
            summary.setAllScansSuccessful(false);
            summary.addMetadata("resourceExhaustion", "true");
            summary.addMetadata("resourceType", e.getResourceType());
            summary.addMetadata("resourceFailureReason", e.getFailureReason());
            summary.addScanResult(createErrorResult(
                "Resource exhaustion detected: " + e.getMessage() + 
                ". Workflow may need to be restarted with adjusted resource limits."
            ));
            
            long totalExecutionTime = System.currentTimeMillis() - workflowStartTime;
            summary.setTotalExecutionTimeMs(totalExecutionTime);
            
            // Throw ResourceExhaustionException - workflow fails
            throw e;
            
        } catch (Exception e) {
            // On exception, don't cleanup - repository may be needed for investigation
            // Only cleanup on successful completion (handled in normal flow above)
            // If cleanup is needed after exception, it should be done manually or by a cleanup process
            
            // Create error summary
            summary.setAllScansSuccessful(false);
            summary.addScanResult(createErrorResult("Workflow execution failed: " + e.getMessage()));
            
            long totalExecutionTime = System.currentTimeMillis() - workflowStartTime;
            summary.setTotalExecutionTimeMs(totalExecutionTime);
            
            throw e;
        }
    }
    
    @Override
    public ScanRequest getOriginalRequest() {
        return originalRequest;
    }
    
    /**
     * Execute a single scan based on scan type
     * Uses configurable timeout if provided in config
     * 
     * Currently only supports BLACKDUCK_DETECT.
     * Structure supports adding additional scan types in the future.
     */
    private ScanResult executeSingleScan(ScanType scanType, String repoPath, ScanRequest request) {
        ScanConfig config = request.getScanConfig();
        
        // Use configurable timeout if provided, otherwise use default
        int timeoutSeconds = (config != null && config.getScanTimeoutSeconds() != null) 
            ? config.getScanTimeoutSeconds() 
            : Shared.SCAN_TIMEOUT_SECONDS;
        
        // Create activity stub with custom timeout if different from default
        BlackDuckScanActivity blackduckStub = blackduckActivity;
        
        if (timeoutSeconds != Shared.SCAN_TIMEOUT_SECONDS) {
            ActivityOptions customOptions = createScanActivityOptions(timeoutSeconds);
            blackduckStub = Workflow.newActivityStub(BlackDuckScanActivity.class, customOptions);
        }
        
        // Currently only BlackDuck is supported
        // Switch statement structure allows adding new scan types in the future
        switch (scanType) {
            case BLACKDUCK_DETECT:
                // Pass the full request to BlackDuck activity (includes BlackDuckConfig)
                return blackduckStub.scanSignatures(repoPath, request);
                
            default:
                ScanResult result = new ScanResult(scanType, false);
                result.setErrorMessage("Unsupported scan type: " + scanType + ". Currently only BLACKDUCK_DETECT is supported.");
                return result;
        }
    }
    
    private ScanResult createErrorResult(String errorMessage) {
        ScanResult result = new ScanResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}

