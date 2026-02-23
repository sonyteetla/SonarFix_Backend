package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.ScanTask;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScanService {

    private final Map<String, ScanTask> scanStore = new ConcurrentHashMap<>();
    private final SonarService sonarService;

    public ScanService(SonarService sonarService) {
        this.sonarService = sonarService;
    }

    // CREATE NEW PROJECT
    public String startNewScan(String projectPath) {

        String executionId = UUID.randomUUID().toString();
        String projectKey = "auto-project-" + executionId;

        return startScanInternal(projectPath, projectKey, executionId);
    }

    //  RE-SCAN EXISTING PROJECT
    public String reScan(String projectPath, String projectKey) {

        String executionId = UUID.randomUUID().toString();

        return startScanInternal(projectPath, projectKey, executionId);
    }

    // Common internal method
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

    // RUN SCAN (ASYNC)
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

    public String getStatus(String scanId) {
        ScanTask task = scanStore.get(scanId);
        return task == null ? "NOT_FOUND" : task.getStatus();
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
}