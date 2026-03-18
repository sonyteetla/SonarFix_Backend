package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.repository.ScanRepository;
import com.company.codequality.sonarautofix.util.ProjectZipUtil;
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
     this.projectService=projectService;
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
        task.setStatus("QUEUED");
        scanRepository.update(task);
        runScanAsync(task);
    }

    private String startScanInternal(String projectPath,
            String projectKey,
            String executionId) {

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
        	scanRepository.update(task);

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
        
            if (task.getSuggestions() == null) {
                task.setSuggestions(new ArrayList<>());
            }
            scanRepository.update(task);
            task.setStatus("COMPLETED");
        
            scanRepository.update(task);
        } catch (Exception e) {

            System.out.println("⚠ Scan completed with build issues (tolerated)");

            task.setStatus("COMPLETED");
            task.setResult("Scan completed with compilation errors in target project.");
            scanRepository.update(task);
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
                        .ruleId(issue.getRuleId())
                        .build();

                requests.add(request);
            }
        }

        if (requests.isEmpty()) {
            throw new IllegalStateException("No auto-fixable issues");
        }

        try {

            // ✅ COPY PROJECT FIRST
            String originalPath = task.getProjectPath();

            String fixedProjectPath =
                    projectUploadService.copyProject(originalPath);

            System.out.println("📂 Fixed project created at: " + fixedProjectPath);

            // ✅ RUN FIXES ON COPY
            autoFixEngine.applyFixes(
                    requests,
                    fixedProjectPath,
                    task.getProjectKey(),
                    scanId
            );
         // run scan once
            reScan(fixedProjectPath, task.getProjectKey());
           
        } catch (Exception e) {

            throw new RuntimeException("AutoFix failed", e);
        }

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
    
    public int autoFixSelected(String scanId, List<String> issueKeys) {

        ScanTask task = scanRepository.findById(scanId);

        if (task == null) {
            throw new IllegalArgumentException("Scan not found");
        }

        List<MappedIssue> issues = task.getMappedIssues();

        if (issues == null || issues.isEmpty()) {
            throw new IllegalStateException("No issues found in scan");
        }

        System.out.println("Selected keys from UI: " + issueKeys);

        List<FixRequest> requests = new ArrayList<>();

        for (MappedIssue issue : issues) {

            // DEBUG
            System.out.println("Checking mapped issue: " + issue.getKey());

            if (!issueKeys.contains(issue.getKey())) {
                continue;
            }

            if (!issue.isAutoFixable()) {
                System.out.println("Skipping non-autofixable issue: " + issue.getKey());
                continue;
            }

            if (issue.getFixType() == null) {
                System.out.println("Skipping issue with null fixType: " + issue.getKey());
                continue;
            }

            String realPath = issue.getFile();

            int idx = realPath.indexOf(":");
            if (idx != -1) {
                realPath = realPath.substring(idx + 1);
            }

            FixRequest request = FixRequest.builder()
                    .filePath(realPath)
                    .line(issue.getLine())
                    .fixType(issue.getFixType())
                    .ruleId(issue.getRuleId())
                    .build();

            requests.add(request);
        }

        if (requests.isEmpty()) {
            throw new IllegalStateException("No selected issues are auto-fixable");
        }

        try {

            String originalPath = task.getProjectPath();

            String fixedProjectPath =
                    projectUploadService.copyProject(originalPath);

            int fixed = autoFixEngine.applyFixes(
                    requests,
                    fixedProjectPath,
                    task.getProjectKey(),
                    scanId
            );

            reScan(fixedProjectPath, task.getProjectKey());

            return fixed;

        } catch (Exception e) {
            throw new RuntimeException("Selected AutoFix failed", e);
        }
    }
    
    public void previewFixes(
            String projectPath,
            String scanId,
            List<Map<String, Object>> fixes
    ) {

        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalArgumentException("Project path is missing from request");
        }

        ScanTask task = scanRepository.findById(scanId);

        if (task == null) {
            throw new IllegalArgumentException("Scan not found");
        }

        try {

            System.out.println("🔥 PREVIEW REQUEST PATH: " + projectPath);

            // ✅ KEEP ORIGINAL SAFE
            String originalPath = projectPath;

            // ✅ CREATE FIXED COPY
            String fixedPath = projectUploadService.copyProject(originalPath);

            if (fixedPath == null || fixedPath.isBlank()) {
                throw new RuntimeException("Failed to create preview project copy");
            }

            System.out.println("📂 Preview project created at: " + fixedPath);

            List<FixRequest> requests = new ArrayList<>();

            for (Map<String, Object> fix : fixes) {

                String fixType = (String) fix.get("fixType");

                if ("METHOD_RENAME".equals(fixType)) {

                    String oldMethod = (String) fix.get("oldMethodName");
                    String newMethod = (String) fix.get("newMethodName");

                    if (oldMethod == null || newMethod == null) {
                        throw new IllegalArgumentException("Method names missing");
                    }

                    System.out.println("🚀 METHOD RENAME: " + oldMethod + " → " + newMethod);

                    FixRequest req = FixRequest.builder()
                            .fixType("METHOD_RENAME")
                            .oldMethodName(oldMethod)
                            .newMethodName(newMethod)
                            .build();

                    requests.add(req);
                }
            }

            if (requests.isEmpty()) {
                throw new IllegalStateException("No valid fixes found");
            }

            // ✅ APPLY FIXES ON FIXED COPY
            autoFixEngine.applyFixes(
                    requests,
                    fixedPath,
                    task.getProjectKey(),
                    scanId
            );

            // 🔥🔥🔥 CRITICAL FIX — SAVE PREVIEW PATH
            task.setPreviewPath(fixedPath);
            scanRepository.update(task);

            System.out.println("✅ Preview path saved: " + fixedPath);

            // ❌ DO NOT CALL RESCAN HERE
            // reScan(fixedPath, task.getProjectKey(), scanId);

        } catch (Exception e) {

            System.err.println("❌ PREVIEW FAILED");
            e.printStackTrace();

            throw new RuntimeException("Preview failed", e);
        }
    }
    
}