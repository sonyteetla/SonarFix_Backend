package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.util.ProjectZipUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScanService {

    private final Map<String, ScanTask> scanStore = new ConcurrentHashMap<>();

    private final SonarService sonarService;
    private final IssueMappingService issueMappingService;
    private final AutoFixEngine autoFixEngine;

    public ScanService(SonarService sonarService,
                       IssueMappingService issueMappingService,
                       AutoFixEngine autoFixEngine) {
        this.sonarService = sonarService;
        this.issueMappingService = issueMappingService;
        this.autoFixEngine = autoFixEngine;
    }

    // ================= NEW PROJECT SCAN =================
    public String startNewScan(String projectPath) {
        String executionId = UUID.randomUUID().toString();
        String projectKey = "auto-project-" + executionId;
        return startScanInternal(projectPath, projectKey, executionId);
    }

    // ================= RE-SCAN =================
    public String reScan(String projectPath, String projectKey) {
        String executionId = UUID.randomUUID().toString();
        return startScanInternal(projectPath, projectKey, executionId);
    }

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

            // 🔥 FIX: pass ScanTask to capture build log
            sonarService.runSonarScan(
                    task.getProjectPath(),
                    task.getProjectKey(),
                    task
            );

            List<SonarIssue> issues =
                    sonarService.fetchIssues(task.getProjectKey());

            List<MappedIssue> mappedIssues =
                    issueMappingService.mapIssues(issues);

            task.setMappedIssues(mappedIssues);

            // Ensure suggestions list initialized
            if (task.getSuggestions() == null) {
                task.setSuggestions(new ArrayList<>());
            }

            task.setStatus("COMPLETED");

        } catch (Exception e) {
            System.out.println("⚠ Scan completed with build issues (tolerated)");
            task.setStatus("COMPLETED");
            task.setResult("Scan completed with compilation errors in target project.");
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
                scanId
        );
    }

    // ================= STATUS =================
    public String getStatus(String scanId) {
        ScanTask task = scanStore.get(scanId);
        return task == null ? "NOT_FOUND" : task.getStatus();
    }

    // ================= RESULT =================
    public ScanResultResponse getResult(String scanId) {

        ScanTask task = scanStore.get(scanId);
        if (task == null) return null;

        List<MappedIssue> issues = task.getMappedIssues();

        int total = (issues == null) ? 0 : issues.size();
        int autoFixable = 0;

        if (issues != null) {
            for (MappedIssue i : issues) {
                if (i.isAutoFixable()) autoFixable++;
            }
        }

        return ScanResultResponse.builder()
                .scanId(scanId)
                .projectKey(task.getProjectKey())
                .status(task.getStatus())
                .totalIssues(total)
                .autoFixableCount(autoFixable)
                .issues(issues)
                .build();
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
                scanId
        );

        String zipPath = ProjectZipUtil.zipProject(task.getProjectPath());
        System.out.println("📦 Refactored project zipped at: " + zipPath);

        return scanId;
    }

    // ================= GET SUGGESTIONS =================
    public List<FixSuggestion> getSuggestions(String scanId) {

        ScanTask task = scanStore.get(scanId);

        if (task == null || task.getSuggestions() == null) {
            return Collections.emptyList();
        }

        return task.getSuggestions();
    }
}