package com.company.codequality.sonarautofix.controller;


import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.repository.ScanRepository;
import com.company.codequality.sonarautofix.service.ScanService;
import com.company.codequality.sonarautofix.util.ProjectZipUtil;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fix")
@CrossOrigin("*")
public class FixController {


    private final ScanService scanService;
    private final ScanRepository scanRepository;
    
    public FixController(ScanService scanService,ScanRepository scanRepository) {
        
        this.scanService = scanService;
        this.scanRepository=scanRepository;
    }

    // ================= APPLY SELECTED FIXES =================
    @PostMapping("/apply/selected")
    public ResponseEntity<?> fixSelected(
            @RequestParam("scanId") String scanId,
            @RequestBody List<String> issueKeys
    ) {

        try {

            if (scanId == null || scanId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("scanId is missing");
            }

            if (issueKeys == null || issueKeys.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("No issue keys provided");
            }

            int fixed = scanService.autoFixSelected(scanId, issueKeys);

            Map<String, Object> response = new HashMap<>();
            response.put("scanId", scanId);
            response.put("fixesApplied", fixed);

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return ResponseEntity.internalServerError()
                    .body("AutoFix Selected failed: " + e.getMessage());
        }
    }

    // ================= AUTO FIX ALL =================
    @PostMapping("/apply/{scanId}")
    public ResponseEntity<?> autoFixAll(@PathVariable("scanId") String scanId) {

        try {
            String updatedScanId = scanService.autoFixAll(scanId);
            return ResponseEntity.ok("AutoFix ALL completed. ScanId: " + updatedScanId);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("AutoFix ALL failed: " + e.getMessage());
        }
    }

    // ================= DOWNLOAD REFACTORED PROJECT =================
    @GetMapping("/download/{scanId}")
    public ResponseEntity<Resource> downloadProject(@PathVariable("scanId") String scanId) throws IOException {

        ScanTask task = scanRepository.findById(scanId);

        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        String projectPath = task.getFixedPath() != null && new File(task.getFixedPath()).exists()
                ? task.getFixedPath()
                : task.getProjectPath();

        // Create ZIP
        String zipPath = ProjectZipUtil.zipProject(projectPath);

        File file = new File(zipPath);

        Resource resource = new UrlResource(file.toURI());

        // ── Determine clean original project name ──────────────────────────
        String downloadName = resolveProjectName(task, projectPath) + "-refactored.zip";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + downloadName + "\""
                )
                .contentLength(file.length())
                .body(resource);
    }

    /** Resolves the clean, human-readable project name for the download filename. */
    private String resolveProjectName(ScanTask task, String fixedProjectPath) {

        // 1) Check for .original-name metadata file in the fixed copy
        try {
            java.nio.file.Path nameFile =
                    java.nio.file.Path.of(fixedProjectPath).resolve(".original-name");
            if (java.nio.file.Files.exists(nameFile)) {
                String name = java.nio.file.Files.readString(nameFile).trim();
                if (!name.isBlank()) return name;
            }
        } catch (Exception ignored) {}

        // 2) Check the original (pre-fix) project path for .original-name
        try {
            String origPath = task.getProjectPath();
            if (origPath != null) {
                java.nio.file.Path nameFile =
                        java.nio.file.Path.of(origPath).resolve(".original-name");
                if (java.nio.file.Files.exists(nameFile)) {
                    String name = java.nio.file.Files.readString(nameFile).trim();
                    if (!name.isBlank()) return name;
                }
            }
        } catch (Exception ignored) {}

        // 3) Use the original project path's folder name (no timestamp for ZIP inner folders)
        try {
            String origPath = task.getProjectPath();
            if (origPath != null) {
                String name = new File(origPath).getName();
                // Strip _fixed suffix
                if (name.endsWith("_fixed")) name = name.substring(0, name.length() - 6);
                // Strip any trailing 13-digit timestamp (with or without underscore prefix)
                name = name.replaceAll("[_-]?\\d{13}$", "").trim();
                if (!name.isBlank() && !name.matches("\\d+")) return name;
            }
        } catch (Exception ignored) {}

        // 4) Last resort: parse the fixed directory name
        String name = new File(fixedProjectPath).getName();
        if (name.endsWith("_fixed")) name = name.substring(0, name.length() - 6);
        name = name.replaceAll("[_-]?\\d{13}$", "").trim();
        if (name.isBlank() || name.matches("\\d+")) name = "project";

        return name;
    }

    // ================= GET SUGGESTIONS =================
    @GetMapping("/suggestions/{scanId}")
    public ResponseEntity<?> getSuggestions(@PathVariable("scanId") String scanId) {
        return ResponseEntity.ok(scanService.getSuggestions(scanId));
    }

    // ================= APPLY FIX BY RULE =================
    @PostMapping("/apply/rule")
    public ResponseEntity<?> fixByRule(
            @RequestParam("scanId") String scanId,
            @RequestParam("ruleId") String ruleId
    ) {

        try {

            if (scanId == null || scanId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("scanId is missing");
            }

            if (ruleId == null || ruleId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("ruleId is missing");
            }

            int fixed = scanService.autoFixByRule(scanId, ruleId);

            Map<String, Object> response = new HashMap<>();
            response.put("scanId", scanId);
            response.put("fixesApplied", fixed);
            response.put("ruleId", ruleId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return ResponseEntity.internalServerError()
                    .body("AutoFix by Rule failed: " + e.getMessage());
        }
    }

    // ================= EXECUTION REPORT =================
    @GetMapping("/report/{scanId}")
    public ResponseEntity<?> getExecutionReport(@PathVariable("scanId") String scanId) {

        ScanTask task =  scanRepository.findById(scanId);
        if (task == null) return ResponseEntity.notFound().build();

        Map<String, Object> response = new HashMap<>();
        response.put("report", task.getFixExecutionReport());
        response.put("totalFixes", task.getTotalFixesApplied());

        return ResponseEntity.ok(response);
    }
}