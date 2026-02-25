package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.model.ScanResultResponse;
import com.company.codequality.sonarautofix.service.ScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    // ==============================
    // START NEW PROJECT SCAN
    // ==============================
    @PostMapping("/start")
    public ResponseEntity<?> startNewScan(@RequestParam String projectPath) {

        String scanId = scanService.startNewScan(projectPath);
        ScanTask task = scanService.getScanTask(scanId);

        return ResponseEntity.ok(
                Map.of(
                        "scanId", scanId,
                        "projectKey", task.getProjectKey(),
                        "status", task.getStatus()
                )
        );
    }

    // ==============================
    // RE-SCAN EXISTING PROJECT
    // ==============================
    @PostMapping("/rescan")
    public ResponseEntity<?> reScan(
            @RequestParam String projectPath,
            @RequestParam String projectKey) {

        String scanId = scanService.reScan(projectPath, projectKey);

        return ResponseEntity.ok(
                Map.of(
                        "scanId", scanId,
                        "projectKey", projectKey,
                        "status", "QUEUED"
                )
        );
    }

    // ==============================
    // CHECK STATUS
    // ==============================
    @GetMapping("/status/{scanId}")
    public ResponseEntity<?> getStatus(@PathVariable String scanId) {

        String status = scanService.getStatus(scanId);

        if ("NOT_FOUND".equals(status)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                Map.of(
                        "scanId", scanId,
                        "status", status
                )
        );
    }

 // ==============================
 // GET RESULT (FINAL OUTPUT)
 // ==============================
 @GetMapping("/result/{scanId}")
 public ResponseEntity<?> getResult(@PathVariable String scanId) {

     ScanResultResponse response = scanService.getResult(scanId);

     // If scan ID not found
     if (response == null) {
         return ResponseEntity.notFound().build();
     }

     // If scan still running or queued
     if (!"COMPLETED".equals(response.getStatus())) {
         return ResponseEntity.ok(
                 Map.of(
                         "scanId", scanId,
                         "projectKey", response.getProjectKey(),
                         "status", response.getStatus(),
                         "message", "Scan is still in progress"
                 )
         );
     }

     // If completed — return full JSON result
     return ResponseEntity.ok(response);
 }
 @GetMapping("/build-log/{scanId}")
 public ResponseEntity<?> getBuildLog(@PathVariable String scanId) {
     ScanTask task = scanService.getScanTask(scanId);
     if (task == null) return ResponseEntity.notFound().build();
     return ResponseEntity.ok(task.getBuildLog());
 }
}