package securityscanapp;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Workflow interface for security scanning operations
 */
@WorkflowInterface
public interface SecurityScanWorkflow {
    
    /**
     * Execute security scans on a repository
     * @param request Scan request containing repository and scan configuration
     * @return Summary of all scan results
     */
    @WorkflowMethod
    ScanSummary executeScans(ScanRequest request);
    
    /**
     * Query method to retrieve the original scan request
     * Used by WorkflowRestartClient to get the request for restarting workflows
     * @return The original ScanRequest that started this workflow
     */
    @QueryMethod
    ScanRequest getOriginalRequest();
}

