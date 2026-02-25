package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.FixRequest;
import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.service.AutoFixEngine;
import com.company.codequality.sonarautofix.service.ScanService;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fix")
public class FixController {

    private final AutoFixEngine autoFixEngine;
    private final ScanService scanService;

    public FixController(AutoFixEngine autoFixEngine,
                         ScanService scanService) {
        this.autoFixEngine = autoFixEngine;
        this.scanService = scanService;
    }

    // ================= APPLY SELECTED FIXES =================
    @PostMapping("/apply")
    public ResponseEntity<?> applySelectedFixes(
            @RequestParam String projectPath,
            @RequestParam String projectKey,
            @RequestBody List<FixRequest> requests) {

        try {

            autoFixEngine.applyFixes(
                    requests,
                    projectPath,
                    projectKey,
                    null
            );

            return ResponseEntity.ok(
                    "Selected fixes applied successfully. Re-scan started.");

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Auto fix failed: " + e.getMessage());
        }
    }

    // ================= AUTO FIX ALL =================
    @PostMapping("/apply/{scanId}")
    public ResponseEntity<?> autoFixAll(@PathVariable String scanId) {

        try {

            String updatedScanId = scanService.autoFixAll(scanId);

            return ResponseEntity.ok(
                    "AutoFix ALL completed. ScanId: " + updatedScanId);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("AutoFix ALL failed: " + e.getMessage());
        }
    }

    // ================= DOWNLOAD REFACTORED PROJECT =================
    @GetMapping("/download/{scanId}")
    public ResponseEntity<Resource> downloadProject(
            @PathVariable String scanId) throws IOException {

        ScanTask task = scanService.getScanTask(scanId);

        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        String workspacePath = task.getProjectPath();
        String zipPath = workspacePath + "-refactored.zip";

        File file = new File(zipPath);

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(file.toURI());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + file.getName())
                .body(resource);
    }

    // ================= GET SUGGESTIONS =================
    @GetMapping("/suggestions/{scanId}")
    public ResponseEntity<?> getSuggestions(@PathVariable String scanId) {
        return ResponseEntity.ok(scanService.getSuggestions(scanId));
    }

    // ================= EXECUTION REPORT FOR UI =================
    @GetMapping("/report/{scanId}")
    public ResponseEntity<?> getExecutionReport(@PathVariable String scanId) {

        ScanTask task = scanService.getScanTask(scanId);

        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("report", task.getFixExecutionReport());
        response.put("totalFixes", task.getTotalFixesApplied());

        return ResponseEntity.ok(response);
    }
}