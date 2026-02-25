package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.FixRequest;
import com.company.codequality.sonarautofix.service.AutoFixEngine;
import com.company.codequality.sonarautofix.service.ScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fix")
@CrossOrigin("*")
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
            @RequestParam("projectPath") String projectPath,
            @RequestParam("projectKey") String projectKey,
            @RequestBody List<FixRequest> requests) {

        try {

            autoFixEngine.applyFixes(
                    requests,
                    projectPath,
                    projectKey,
                    null);

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

            String newScanId = scanService.autoFixAll(scanId);

            return ResponseEntity.ok(
                    "AutoFix ALL completed. New ScanId: " + newScanId);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("AutoFix ALL failed: " + e.getMessage());
        }
    }
}