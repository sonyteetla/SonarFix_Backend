package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.repository.ScanRepository;
import com.company.codequality.sonarautofix.util.ProjectZipUtil;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ScanService {

    private final SonarService sonarService;
    private final AutoFixEngine autoFixEngine;
    private final ProjectUploadService projectUploadService;
    private final ScanIssueService scanIssueService;
    private final ProjectService projectService;
    private final ScanRepository scanRepository;

    public ScanService(SonarService sonarService,
                       ProjectUploadService projectUploadService,
                       AutoFixEngine autoFixEngine,
                       ScanIssueService scanIssueService,
                       ProjectService projectService,
                       ScanRepository scanRepository) {

        this.sonarService = sonarService;
        this.projectUploadService = projectUploadService;
        this.autoFixEngine = autoFixEngine;
        this.scanIssueService = scanIssueService;
        this.projectService = projectService;
        this.scanRepository = scanRepository;
    }

    // ================= NEW PROJECT SCAN =================
    public String startNewScan(String projectPath) {

        String executionId = UUID.randomUUID().toString();
        String projectKey = "auto-project-" + executionId;

        Project project = Project.builder()
                .id(projectKey)
                .description("Auto-scan for: " + projectPath)
                .build();

        projectService.registerProject(project);
        projectUploadService.registerProjectKey(projectPath, projectKey);

        return startScanInternal(projectPath, projectKey, executionId);
    }
    
    //Manual Rescan
    public String reScan(String projectPath, String projectKey) {

        String executionId = UUID.randomUUID().toString();
        return startScanInternal(projectPath, projectKey, executionId);
    }

    // ================= AUTO FIX RE-SCAN =================
    public void reScan(String projectPath,
            String projectKey,
            String scanId) {

      ScanTask task = scanRepository.findById(scanId);
      if (task == null) {
        throw new IllegalArgumentException("Scan not found");
     }
      task.setStatus("QUEUED");
      task.setMappedIssues(null);
      task.setResult(null);
      scanRepository.save(task);
      runScanAsync(task);
    }

    private String startScanInternal(String projectPath,
                                     String projectKey,
                                     String executionId) {

        ScanTask task = new ScanTask(executionId, projectPath);
        task.setProjectKey(projectKey);
        task.setStatus("QUEUED");

        scanRepository.save(task);
        runScanAsync(task);

        return executionId;
    }

    // ================= RUN SCAN =================
    @Async
    public void runScanAsync(ScanTask task) {

        try {
            task.setStatus("RUNNING");
            scanRepository.save(task);

            // Run sonar once
            sonarService.runSonarScan(
                    task.getProjectPath(),
                    task.getProjectKey(),
                    task
            );

            // Retry fetching issues
            List<Issue> issues = new ArrayList<>();
            int maxRetries = 5;

            for (int i = 0; i < maxRetries; i++) {

                issues = scanIssueService.fetchAllIssues(
                        task.getProjectKey(), null, null, null);

                if (!issues.isEmpty()) break;

                Thread.sleep(3000);
            }

            List<MappedIssue> mappedIssues =
                    scanIssueService.toMappedIssues(issues);

            task.setMappedIssues(mappedIssues);
            task.setStatus("COMPLETED");

            scanRepository.save(task);

        } catch (Exception e) {

            task.setStatus("FAILED");
            task.setResult(e.getMessage());
            scanRepository.save(task);
        }
    }

    // ================= APPLY AUTO FIX =================
    public int applyAutoFix(String scanId,
                            List<FixRequest> requests) {

        ScanTask task = scanRepository.findById(scanId);

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

    // ================= AUTO FIX ALL =================
    public String autoFixAll(String scanId) {

        ScanTask task = scanRepository.findById(scanId);

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
                                issue.getFixType()));
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

        return scanId;
    }

    // ================= STATUS =================
    public String getStatus(String scanId) {
        ScanTask task = scanRepository.findById(scanId);
        return task == null ? "NOT_FOUND" : task.getStatus();
    }

    public String getResult(String scanId) {
        ScanTask task = scanRepository.findById(scanId);

        if (task == null) return "NOT_FOUND";
        if (!"COMPLETED".equals(task.getStatus()))
            return "SCAN_NOT_FINISHED";

        return task.getResult();
    }

    public ScanTask getScanTask(String scanId) {
        return scanRepository.findById(scanId);
    }

    public List<ScanTask> getAllScans() {
        return scanRepository.findAll();
    }

    // ================= GET SUGGESTIONS =================
    public List<FixSuggestion> getSuggestions(String scanId) {

        ScanTask task = scanRepository.findById(scanId);

        if (task == null || task.getSuggestions() == null) {
            return Collections.emptyList();
        }

        return task.getSuggestions();
    }
}
