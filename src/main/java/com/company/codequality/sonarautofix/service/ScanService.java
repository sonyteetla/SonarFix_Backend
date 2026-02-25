package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;


import com.company.codequality.sonarautofix.repository.ScanRepository;

import com.company.codequality.sonarautofix.util.ProjectZipUtil;


import com.company.codequality.sonarautofix.repository.ScanRepository;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScanService {

    private final SonarService sonarService;
    private final AutoFixEngine autoFixEngine;
    private final ProjectUploadService projectUploadService;
    private final ScanIssueService scanIssueService;
    private final ProjectService projectService;
    private final ScanRepository scanRepository;

    public ScanService(SonarService sonarService, ProjectUploadService projectUploadService,
            AutoFixEngine autoFixEngine, ScanIssueService scanIssueService, ProjectService projectService,
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

        // NEW: Register project metadata so it shows up in dashboard counts!
        Project project = Project.builder()
                .projectKey(projectKey)
                .description("Auto-scan for: " + projectPath)
                .build();
        projectService.registerProject(project);

        // register metadata locally in the project folder
        projectUploadService.registerProjectKey(projectPath, projectKey);
        return startScanInternal(projectPath, projectKey, executionId);
    }



    // NEW: re-scan with SAME scanId (used after AutoFix)

    // ================= RE-SCAN =================
    public String reScan(String projectPath, String projectKey) {
        String executionId = UUID.randomUUID().toString();
        return startScanInternal(projectPath, projectKey, executionId);
    }


    // NEW: re-scan with SAME scanId (used after AutoFix)

    public void reScan(String projectPath, String projectKey, String scanId) {
        ScanTask task = scanRepository.findById(scanId);
        if (task == null) {
            throw new IllegalArgumentException("Scan not found");
        }
        task.setStatus("QUEUED");
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


            // SONAR EXECUTION
            String sonarOutput = sonarService.runSonarScan(
                    task.getProjectPath(),
                    task.getProjectKey());

            task.setResult(sonarOutput);

            // NEW: Fetch issues and populate task with retry logic!
            List<Issue> issues = new java.util.ArrayList<>();
            int maxRetries = 5;
            int retryDelayMs = 3000;


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



            for (int i = 0; i < maxRetries; i++) {
                issues = scanIssueService.fetchAllIssues(task.getProjectKey(), null, null, null);

                if (!issues.isEmpty()) {
                    break;
                }

                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ignored) {
                    }
                }
            }

            List<MappedIssue> mappedIssues = scanIssueService.toMappedIssues(issues);
            task.setMappedIssues(mappedIssues);



            // Ensure suggestions list initialized
            if (task.getSuggestions() == null) {
                task.setSuggestions(new ArrayList<>());
            }



            task.setStatus("COMPLETED");
            scanRepository.save(task);

        } catch (Exception e) {

            task.setStatus("FAILED");
            task.setResult(e.getMessage());
            scanRepository.save(task);


            System.out.println("⚠ Scan completed with build issues (tolerated)");
            task.setStatus("COMPLETED");
            task.setResult("Scan completed with compilation errors in target project.");


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


                scanId // important (same scanId re-used)

                scanId


                scanId // important (same scanId re-used)

        );
    }

    // ================= STATUS =================
    public String getStatus(String scanId) {
        ScanTask task = scanRepository.findById(scanId);
        return task == null ? "NOT_FOUND" : task.getStatus();
    }

    // RE-SCAN EXISTING PROJECT
    public String reScan(String projectPath, String projectKey) {

        String executionId = UUID.randomUUID().toString();

        return startScanInternal(projectPath, projectKey, executionId);
    }

    public String getResult(String scanId) {
        ScanTask task = scanRepository.findById(scanId);

        if (task == null) {
            return "NOT_FOUND";
        }

        if (!"COMPLETED".equals(task.getStatus())) {
            return "SCAN_NOT_FINISHED";
        }

        return task.getResult();
    }

    public ScanTask getScanTask(String scanId) {
        return scanRepository.findById(scanId);
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

                scanId // re-use same scan
        );

        return scanId; // same scan updated after re-scan
    }

    public List<ScanTask> getAllScans() {
        return scanRepository.findAll();


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