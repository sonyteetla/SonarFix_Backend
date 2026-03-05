package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.service.ScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/scan")
@CrossOrigin("*")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    // =====================================
    // START NEW PROJECT SCAN
    // =====================================
    @PostMapping("/start")
    public ResponseEntity<?> startNewScan(
            @RequestParam("projectPath") String projectPath) {

        String scanId = scanService.startNewScan(projectPath);
        ScanTask task = scanService.getScanTask(scanId);

        if (task == null) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to create scan task"));
        }

        return ResponseEntity.ok(Map.of(
                "scanId", scanId,
                "projectKey", task.getProjectKey(),
                "status", task.getStatus()
        ));
    }

    // =====================================
    // RE-SCAN EXISTING PROJECT
    // =====================================
    @PostMapping("/rescan")
    public ResponseEntity<?> reScan(
            @RequestParam("projectPath") String projectPath,
            @RequestParam("projectKey") String projectKey) {

        String scanId = scanService.reScan(projectPath, projectKey);
        ScanTask task = scanService.getScanTask(scanId);

        if (task == null) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to create rescan task"));
        }

        return ResponseEntity.ok(Map.of(
                "scanId", scanId,
                "projectKey", task.getProjectKey(),
                "status", task.getStatus()
        ));
    }

    // =====================================
    // CHECK SCAN STATUS
    // =====================================
    @GetMapping("/status/{scanId}")
    public ResponseEntity<?> getStatus(
            @PathVariable("scanId") String scanId) {

        ScanTask task = scanService.getScanTask(scanId);

        if (task == null) {
            return ResponseEntity.notFound()
                    .build();
        }

        return ResponseEntity.ok(Map.of(
                "scanId", scanId,
                "projectKey", task.getProjectKey(),
                "status", task.getStatus()
        ));
    }

    // =====================================
    // GET FINAL RESULT (WHEN COMPLETED)
    // =====================================
    @GetMapping("/result/{scanId}")
    public ResponseEntity<?> getResult(
            @PathVariable("scanId") String scanId) {

        ScanTask task = scanService.getScanTask(scanId);

        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        if (!"COMPLETED".equals(task.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Scan not completed yet"));
        }

        return ResponseEntity.ok(
                scanService.getResult(scanId)
        );
    }

    // =====================================
    // GET BUILD LOG
    // =====================================
    @GetMapping("/build-log/{scanId}")
    public ResponseEntity<?> getBuildLog(
            @PathVariable String scanId) {

        ScanTask task = scanService.getScanTask(scanId);

        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "scanId", scanId,
                "projectKey", task.getProjectKey(),
                "status", task.getStatus(),
                "buildLog", task.getBuildLog()
        ));
    }
}
