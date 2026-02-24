package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScanService {

    private final Map<String, ScanTask> scanStore = new ConcurrentHashMap<>();

    private final SonarService sonarService;
    private final AutoFixEngine autoFixEngine;

    
    public ScanService(SonarService sonarService,
                       AutoFixEngine autoFixEngine) {
        this.sonarService = sonarService;
        this.autoFixEngine = autoFixEngine;
    }

    // ================= NEW PROJECT SCAN =================
    public String startNewScan(String projectPath) {
        String executionId = UUID.randomUUID().toString();
        String projectKey = "auto-project-" + executionId;
        return startScanInternal(projectPath, projectKey, executionId);
    }

    // 🔥 NEW: re-scan with SAME scanId (used after AutoFix)
    public void reScan(String projectPath, String projectKey, String scanId) {
        ScanTask task = scanStore.get(scanId);
        if (task == null) {
            throw new IllegalArgumentException("Scan not found");
        }

        task.setStatus("QUEUED");
        runScanAsync(task);
    }

    private String startScanInternal(String projectPath,
                                     String projectKey,
                                     String executionId) {

        ScanTask task = new ScanTask(executionId, projectPath);
        task.setProjectKey(projectKey);
        task.setStatus("QUEUED");

        scanStore.put(executionId, task);
        runScanAsync(task);

        return executionId;
    }

    // ================= RUN SCAN =================
    @Async
    public void runScanAsync(ScanTask task) {

        try {
            task.setStatus("RUNNING");

            // SONAR EXECUTION 
            String sonarOutput =
            	    sonarService.runSonarScan(
            	        task.getProjectPath(),
            	        task.getProjectKey()
            	    );
            
            task.setResult(sonarOutput);
            task.setStatus("COMPLETED");

        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setResult(e.getMessage());
        }
    }


    // ================= APPLY AUTO FIX =================
    public int applyAutoFix(String scanId,
                            List<FixRequest> requests) {

        ScanTask task = scanStore.get(scanId);

        if (task == null) {
            throw new IllegalArgumentException("Scan not found");
        }

        return autoFixEngine.applyFixes(
                requests,
                task.getProjectPath(),
                task.getProjectKey(),
                scanId   //  important (same scanId re-used)
        );
    }

    // ================= STATUS =================
    public String getStatus(String scanId) {
        ScanTask task = scanStore.get(scanId);
        return task == null ? "NOT_FOUND" : task.getStatus();
    }

    //  RE-SCAN EXISTING PROJECT
    public String reScan(String projectPath, String projectKey) {

        String executionId = UUID.randomUUID().toString();

        return startScanInternal(projectPath, projectKey, executionId);
    }

    public String getResult(String scanId) {
        ScanTask task = scanStore.get(scanId);

        if (task == null) {
            return "NOT_FOUND";
        }

        if (!"COMPLETED".equals(task.getStatus())) {
            return "SCAN_NOT_FINISHED";
        }

        return task.getResult();
    }

    public ScanTask getScanTask(String scanId) {
        return scanStore.get(scanId);
    }

    // ================= AUTO FIX ALL =================
    public String autoFixAll(String scanId) {

        ScanTask task = scanStore.get(scanId);

        if (task == null) {
            throw new IllegalArgumentException("Scan not found");
        }

        if (!"COMPLETED".equals(task.getStatus())) {
            throw new IllegalStateException("Scan not completed yet");
        }

        List<MappedIssue> issues = task.getMappedIssues();

        if (issues == null || issues.isEmpty()) {
            throw new IllegalStateException("No issues found");
        }

        List<FixRequest> requests = new ArrayList<>();

        for (MappedIssue issue : issues) {
            if (issue.isAutoFixable() && issue.getFixType() != null) {

                String realPath = issue.getFile();

                // Remove sonar prefix: projectKey:
                int idx = realPath.indexOf(":");
                if (idx != -1) {
                    realPath = realPath.substring(idx + 1);
                }

                requests.add(
                        new FixRequest(
                                realPath,
                                issue.getLine(),
                                issue.getFixType()
                        )
                );
            }
        }

        if (requests.isEmpty()) {
            throw new IllegalStateException("No auto-fixable issues");
        }

        autoFixEngine.applyFixes(
                requests,
                task.getProjectPath(),
                task.getProjectKey(),
                scanId   //  re-use same scan
        );

        return scanId;   // same scan updated after re-scan
    }
}