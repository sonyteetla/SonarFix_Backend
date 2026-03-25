package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.repository.ScanRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class ScanService {

    private final ScanRepository scanRepository;
    private final ProjectService projectService;
    private final SonarService sonarService;
    private final IssueMappingService issueMappingService;
    private final AutoFixEngine autoFixEngine;
    private final ProjectUploadService projectUploadService;

    public ScanService(SonarService sonarService, ProjectService projectService,
            IssueMappingService issueMappingService,
            AutoFixEngine autoFixEngine,
            ProjectUploadService projectUploadService,
            ScanRepository scanRepository) {
        this.sonarService = sonarService;
        this.projectService = projectService;
        this.issueMappingService = issueMappingService;
        this.autoFixEngine = autoFixEngine;
        this.projectUploadService = projectUploadService;
        this.scanRepository = scanRepository;
    }

    // ================= GET ALL SCANS =================
    public List<ScanTask> getAllScans() {
        return scanRepository.findAll();
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
        ScanTask task = scanRepository.findById(scanId);
        if (task == null) {
            throw new IllegalArgumentException("Scan not found");
        }

        // Preserve fix metadata before reset
        int totalFixesApplied = task.getTotalFixesApplied();
        int originalIssueCount = task.getOriginalIssueCount();
        Map<String, Integer> fixReport = task.getFixExecutionReport();
        String fixedPath = task.getFixedPath();

        // Full reset of scan state only
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setResult(null);
        task.setMappedIssues(new ArrayList<>());
        task.setSuggestions(new ArrayList<>());
        task.setBuildLog("");

        // Restore fix metadata so it isn't wiped on rescan
        task.setTotalFixesApplied(totalFixesApplied);
        task.setOriginalIssueCount(originalIssueCount);
        task.setFixExecutionReport(fixReport);
        task.setFixedPath(fixedPath);

        // Update projectPath to fixed path for rescan
        task.setProjectPath(projectPath);

        scanRepository.update(task);
        runScanAsync(task);
    }
    private String startScanInternal(String projectPath, String projectKey, String executionId) {
        if (projectPath == null || projectPath.isEmpty()) {
            throw new IllegalArgumentException("Project path cannot be null");
        }

        ScanTask task = new ScanTask();
        task.setScanId(executionId);
        task.setProjectKey(projectKey);
        task.setProjectPath(projectPath);
        task.setStatus("QUEUED");

        scanRepository.save(task);
        projectService.registerProject(projectKey, projectPath);
        runScanAsync(task);

        return executionId;
    }

    // ================= RUN SCAN =================
    @Async
    public void runScanAsync(ScanTask task) {
        try {
            task.setStatus("RUNNING");
            task.setProgress(5);
            scanRepository.update(task);

            // STEP 1: Maven + Sonar scan (0–70 handled inside SonarService)
            sonarService.runSonarScan(task.getProjectPath(), task.getProjectKey(), task);

            // STEP 2: Fetch issues
            List<SonarIssue> issues = sonarService.fetchIssues(task.getProjectKey());
            task.setProgress(80);
            scanRepository.update(task);

            // STEP 3: Map issues
            List<MappedIssue> mappedIssues = issueMappingService.mapIssues(issues);
            task.setMappedIssues(mappedIssues);

            task.setProgress(90);
            scanRepository.update(task);

            if (task.getSuggestions() == null) {
                task.setSuggestions(new ArrayList<>());
            }

            // FINAL
            task.setStatus("COMPLETED");
            task.setProgress(100);
            scanRepository.update(task);

        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setResult("Scan failed: " + e.getMessage());
            task.setProgress(100);
            scanRepository.update(task);
            throw new RuntimeException(e);
        }
    }
    // ================= APPLY AUTO FIX =================
    public int applyAutoFix(String scanId, List<FixRequest> requests) {
        ScanTask task = scanRepository.findById(scanId);
        if (task == null) {
            throw new IllegalArgumentException("Scan not found");
        }
        return autoFixEngine.applyFixes(requests, task.getProjectPath(), task.getProjectKey(), scanId);
    }

    // ================= STATUS =================
    public String getStatus(String scanId) {
        ScanTask task = scanRepository.findById(scanId);
        return task == null ? "NOT_FOUND" : task.getStatus();
    }

    // ================= RESULT =================
    public ScanResultResponse getResult(String scanId) {
        ScanTask task = scanRepository.findById(scanId);
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
        return scanRepository.findById(scanId);
    }

    // ================= AUTO FIX ALL =================
    public String autoFixAll(String scanId) {
        ScanTask task = scanRepository.findById(scanId);
        if (task == null) throw new IllegalArgumentException("Scan not found");
        if (!"COMPLETED".equals(task.getStatus())) throw new IllegalStateException("Scan not completed yet");

        List<MappedIssue> issues = task.getMappedIssues();
        if (issues == null || issues.isEmpty()) throw new IllegalStateException("No issues found");

        List<FixRequest> requests = new ArrayList<>();
        for (MappedIssue issue : issues) {
            if (issue.isAutoFixable() && issue.getFixType() != null) {
                String realPath = issue.getFile();
                int idx = realPath.indexOf(":");
                if (idx != -1) realPath = realPath.substring(idx + 1);

                requests.add(FixRequest.builder()
                        .filePath(realPath)
                        .line(issue.getLine())
                        .fixType(issue.getFixType())
                        .ruleId(issue.getRuleId())
                        .build());
            }
        }

        if (requests.isEmpty()) throw new IllegalStateException("No auto-fixable issues");

        try {
            String fixedProjectPath = task.getFixedPath();
            if (fixedProjectPath == null || !new java.io.File(fixedProjectPath).exists()) {
                fixedProjectPath = projectUploadService.copyProject(task.getProjectPath());
                task.setFixedPath(fixedProjectPath);
                scanRepository.update(task);
            }

            int fixed = autoFixEngine.applyFixes(requests, fixedProjectPath, task.getProjectKey(), scanId);

            // ✅ Snapshot original issue count once
            if (task.getOriginalIssueCount() == 0) {
                task.setOriginalIssueCount(issues.size());
            }
            task.setTotalFixesApplied(task.getTotalFixesApplied() + fixed);

            Map<String, Integer> fixReport = task.getFixExecutionReport();
            for (FixRequest req : requests) {
            	if (req.getFixType() != null) {
            		fixReport.merge(req.getFixType(), 1, Integer::sum);
            	}
            }
            task.setFixExecutionReport(fixReport);
            scanRepository.update(task);

            reScan(fixedProjectPath, task.getProjectKey(), scanId);

        } catch (Exception e) {
            throw new RuntimeException("AutoFix failed", e);
        }

        return scanId;
    }

    // ================= GET SUGGESTIONS =================
    public List<FixSuggestion> getSuggestions(String scanId) {
        ScanTask task = scanRepository.findById(scanId);
        if (task == null || task.getSuggestions() == null) return Collections.emptyList();
        return task.getSuggestions();
    }

    // ================= AUTO FIX SELECTED =================
    public int autoFixSelected(String scanId, List<String> issueKeys) {
        ScanTask task = scanRepository.findById(scanId);
        if (task == null) throw new IllegalArgumentException("Scan not found");

        List<MappedIssue> issues = task.getMappedIssues();
        if (issues == null || issues.isEmpty()) throw new IllegalStateException("No issues found in scan");

        System.out.println("Selected keys from UI: " + issueKeys);

        List<FixRequest> requests = new ArrayList<>();
        for (MappedIssue issue : issues) {
            System.out.println("Checking mapped issue: " + issue.getKey());

            if (!issueKeys.contains(issue.getKey())) continue;
            if (!issue.isAutoFixable()) {
                System.out.println("Skipping non-autofixable: " + issue.getKey());
                continue;
            }
            if (issue.getFixType() == null) {
                System.out.println("Skipping null fixType: " + issue.getKey());
                continue;
            }

            String realPath = issue.getFile();
            int idx = realPath.indexOf(":");
            if (idx != -1) realPath = realPath.substring(idx + 1);

            requests.add(FixRequest.builder()
                    .filePath(realPath)
                    .line(issue.getLine())
                    .fixType(issue.getFixType())
                    .ruleId(issue.getRuleId())
                    .build());
        }

        if (requests.isEmpty()) throw new IllegalStateException("No selected issues are auto-fixable");

        try {
            String fixedProjectPath = task.getFixedPath();
            if (fixedProjectPath == null || !new java.io.File(fixedProjectPath).exists()) {
                fixedProjectPath = projectUploadService.copyProject(task.getProjectPath());
                task.setFixedPath(fixedProjectPath);
                scanRepository.update(task);
            }

            int fixed = autoFixEngine.applyFixes(requests, fixedProjectPath, task.getProjectKey(), scanId);

            // ✅ Snapshot original issue count once
            if (task.getOriginalIssueCount() == 0) {
                task.setOriginalIssueCount(issues.size());
            }
            // ✅ Single increment — no duplicate
            task.setTotalFixesApplied(task.getTotalFixesApplied() + fixed);

            Map<String, Integer> fixReport = task.getFixExecutionReport();
            for (FixRequest req : requests) {
            	if (req.getFixType() != null) {
            		fixReport.merge(req.getFixType(), 1, Integer::sum);
            	}
            }
            task.setFixExecutionReport(fixReport);
            scanRepository.update(task);

            reScan(fixedProjectPath, task.getProjectKey(), scanId);

            return fixed;

        } catch (Exception e) {
            throw new RuntimeException("Selected AutoFix failed", e);
        }
    }

    // ================= PREVIEW FIXES =================
    public String previewFixes(String scanId) {
        ScanTask task = scanRepository.findById(scanId);
        if (task == null) throw new IllegalArgumentException("Scan not found");

        List<MappedIssue> issues = task.getMappedIssues();
        if (issues == null || issues.isEmpty()) {
            System.out.println("PreviewFixes: No cached issues. Fetching live for: " + task.getProjectKey());
            try {
                List<SonarIssue> sonarIssues = sonarService.fetchIssues(task.getProjectKey());
                issues = issueMappingService.mapIssues(sonarIssues);
                task.setMappedIssues(issues);
                scanRepository.update(task);
            } catch (Exception e) {
                System.err.println("Preview fallback fetch failed: " + e.getMessage());
            }
        }

        if (issues == null || issues.isEmpty()) return task.getProjectPath();

        List<FixRequest> requests = new ArrayList<>();
        for (MappedIssue issue : issues) {
            if (!issue.isAutoFixable() || issue.getFixType() == null) continue;

            String realPath = issue.getFile();
            int idx = realPath.indexOf(":");
            if (idx != -1) realPath = realPath.substring(idx + 1);

            requests.add(FixRequest.builder()
                    .filePath(realPath)
                    .line(issue.getLine())
                    .fixType(issue.getFixType())
                    .ruleId(issue.getRuleId())
                    .build());
        }

        if (requests.isEmpty()) return task.getProjectPath();

        try {
            String previewPath = task.getFixedPath();
            if (previewPath == null || !new java.io.File(previewPath).exists()) {
                previewPath = projectUploadService.copyProject(task.getProjectPath());
                task.setFixedPath(previewPath);
                scanRepository.update(task);
            }

            autoFixEngine.applyFixes(requests, previewPath, task.getProjectKey(), scanId);
            return previewPath;

        } catch (Exception e) {
            throw new RuntimeException("Preview generation failed", e);
        }
    }

    // ================= AUTO FIX BY RULE =================
    public int autoFixByRule(String scanId, String ruleId) {
        ScanTask task = scanRepository.findById(scanId);
        if (task == null) throw new IllegalArgumentException("Scan not found");

        List<MappedIssue> issues = task.getMappedIssues();
        if (issues == null || issues.isEmpty()) {
            System.out.println("No cached issues. Fetching live for: " + task.getProjectKey());
            try {
                List<SonarIssue> sonarIssues = sonarService.fetchIssues(task.getProjectKey());
                issues = issueMappingService.mapIssues(sonarIssues);
            } catch (Exception e) {
                System.err.println("Failed to fetch live issues: " + e.getMessage());
            }
        }

        if (issues == null || issues.isEmpty()) {
            throw new IllegalStateException("No auto-fixable issues found for scan " + scanId);
        }

        List<FixRequest> requests = new ArrayList<>();
        for (MappedIssue issue : issues) {
            if (!ruleId.equals(issue.getRuleId())) continue;
            if (!issue.isAutoFixable()) continue;
            if (issue.getFixType() == null) continue;

            String realPath = issue.getFile();
            int idx = realPath.indexOf(":");
            if (idx != -1) realPath = realPath.substring(idx + 1);

            requests.add(FixRequest.builder()
                    .filePath(realPath)
                    .line(issue.getLine())
                    .fixType(issue.getFixType())
                    .ruleId(issue.getRuleId())
                    .build());
        }

        if (requests.isEmpty()) throw new IllegalStateException("No auto-fixable issues found for rule: " + ruleId);

        try {
            String fixedProjectPath = task.getFixedPath();
            if (fixedProjectPath == null || !new java.io.File(fixedProjectPath).exists()) {
                fixedProjectPath = projectUploadService.copyProject(task.getProjectPath());
                task.setFixedPath(fixedProjectPath);
                scanRepository.update(task);
            }

            int fixed = autoFixEngine.applyFixes(requests, fixedProjectPath, task.getProjectKey(), scanId);

            // ✅ Snapshot original issue count once
            if (task.getOriginalIssueCount() == 0) {
                task.setOriginalIssueCount(issues.size());
            }
            // ✅ Single increment — no duplicate
            task.setTotalFixesApplied(task.getTotalFixesApplied() + fixed);

            Map<String, Integer> report = task.getFixExecutionReport();
            for (FixRequest req : requests) {
                if (req.getFixType() != null) {
                	report.merge(req.getFixType(), 1, Integer::sum);
                }
            }
            task.setFixExecutionReport(report);
            scanRepository.update(task);

            System.out.println("Applied " + fixed + " fixes for rule " + ruleId + ". Running re-scan...");
            reScan(fixedProjectPath, task.getProjectKey(), scanId);

            return fixed;

        } catch (Exception e) {
            String msg = (e instanceof RuntimeException && e.getCause() != null)
                    ? e.getCause().getMessage() : e.getMessage();
            System.err.println("RULE-LEVEL FIX FAILURE: scanId=" + scanId + ", ruleId=" + ruleId);
            throw new RuntimeException("Fix Operation Failed: " + (msg != null ? msg : e.toString()), e);
        }
    }
}