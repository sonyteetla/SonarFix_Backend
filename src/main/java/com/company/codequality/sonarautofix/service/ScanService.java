package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.repository.ScanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final SonarService sonarService;
    private final IssueMappingService issueMappingService;
    private final AutoFixEngine autoFixEngine;
    private final ScanRepository scanRepository;

    public ScanService(SonarService sonarService,
            IssueMappingService issueMappingService,
            AutoFixEngine autoFixEngine,
            ScanRepository scanRepository) {
        this.sonarService = sonarService;
        this.issueMappingService = issueMappingService;
        this.autoFixEngine = autoFixEngine;
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
        task.setStatus("QUEUED");
        runScanAsync(task);
    }

    private String startScanInternal(String projectPath,
            String projectKey,
            String executionId) {

        ScanTask task = new ScanTask(executionId, projectPath);
        task.setProjectKey(projectKey);
        task.setStatus("QUEUED");

        scanRepository.save(task); // persist immediately
        runScanAsync(task);

        return executionId;
    }

    // ================= RUN SCAN =================
    @Async
    public void runScanAsync(ScanTask task) {

        try {
            task.setStatus("RUNNING");
            scanRepository.save(task);

            sonarService.runSonarScan(
                    task.getProjectPath(),
                    task.getProjectKey(),
                    task);

            List<SonarIssue> issues = sonarService.fetchIssues(task.getProjectKey());

            List<MappedIssue> mappedIssues = issueMappingService.mapIssues(issues);

            task.setMappedIssues(mappedIssues);

            if (task.getSuggestions() == null) {
                task.setSuggestions(new ArrayList<>());
            }

            task.setStatus("COMPLETED");
            scanRepository.save(task); // persist final state

        } catch (Exception e) {
            log.error("Scan failed: {}", e.getMessage(), e);
            task.setStatus("FAILED");
            task.setBuildLog(task.getBuildLog() + "\nSCAN ERROR: " + e.getMessage());
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
                scanId);
    }

    // ================= STATUS =================
    public String getStatus(String scanId) {
        ScanTask task = scanRepository.findById(scanId);
        return task == null ? "NOT_FOUND" : task.getStatus();
    }

    // ================= RESULT =================
    public ScanResultResponse getResult(String scanId) {

        ScanTask task = scanRepository.findById(scanId);
        if (task == null)
            return null;

        List<MappedIssue> issues = task.getMappedIssues();

        int total = (issues == null) ? 0 : issues.size();
        int autoFixable = 0;

        if (issues != null) {
            for (MappedIssue i : issues) {
                if (i.isAutoFixable())
                    autoFixable++;
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

                FixRequest request = FixRequest.builder()
                        .filePath(realPath)
                        .line(issue.getLine())
                        .fixType(issue.getFixType())
                        .ruleId(issue.getRuleId()) // ✅ IMPORTANT FIX
                        .build();

                requests.add(request);
            }
        }

        if (requests.isEmpty()) {
            throw new IllegalStateException("No auto-fixable issues");
        }

        autoFixEngine.applyFixes(
                requests,
                task.getProjectPath(),
                task.getProjectKey(),
                scanId);

        // Note: ProjectZipUtil.zipProject() is already called inside
        // AutoFixEngine.applyFixes()
        log.info("AutoFixAll complete for scanId={}. Zip ready for download.", scanId);

        return scanId;
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