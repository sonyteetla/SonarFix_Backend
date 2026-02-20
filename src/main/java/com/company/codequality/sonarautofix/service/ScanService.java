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

    // 🔗 Inject SonarService
    public ScanService(SonarService sonarService) {
        this.sonarService = sonarService;
    }

    // ==============================
    // 🚀 START SCAN
    // ==============================
    public String startScan(String projectPath) {

        String scanId = UUID.randomUUID().toString();

        ScanTask task = new ScanTask(scanId, projectPath);
        task.setStatus("QUEUED");

        scanStore.put(scanId, task);

        // Run scan in background
        runScanAsync(task);

        return scanId;
    }

    // ==============================
    // ⚙️ RUN SCAN (ASYNC)
    // ==============================
    @Async
    public void runScanAsync(ScanTask task) {

        try {
            task.setStatus("RUNNING");

            // 🔥 REAL SONAR SCAN EXECUTION
            String sonarOutput = sonarService.runSonarScan(task.getProjectPath());

            task.setResult(sonarOutput);
            task.setStatus("COMPLETED");

        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setResult(e.getMessage());
        }
    }

    // ==============================
    // 📊 GET SCAN STATUS
    // ==============================
    public String getStatus(String scanId) {
        ScanTask task = scanStore.get(scanId);
        return task == null ? "NOT_FOUND" : task.getStatus();
    }

    // ==============================
    // 📄 GET SCAN RESULT
    // ==============================
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
}
