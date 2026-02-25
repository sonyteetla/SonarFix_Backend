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

        // ==============================
        // START NEW PROJECT SCAN
        // ==============================
        @PostMapping("/start")
        public ResponseEntity<?> startNewScan(@RequestParam("projectPath") String projectPath) {

                String scanId = scanService.startNewScan(projectPath);
                ScanTask task = scanService.getScanTask(scanId);

                return ResponseEntity.ok(
                                Map.of(
                                                "scanId", scanId,
                                                "projectKey", task.getProjectKey(),
                                                "status", task.getStatus()));
        }

        // ==============================
        // RE-SCAN EXISTING PROJECT
        // ==============================
        @PostMapping("/rescan")
        public ResponseEntity<?> reScan(
                        @RequestParam("projectPath") String projectPath,
                        @RequestParam("projectKey") String projectKey) {

                String scanId = scanService.reScan(projectPath, projectKey);

                return ResponseEntity.ok(
                                Map.of(
                                                "scanId", scanId,
                                                "projectKey", projectKey,
                                                "status", "QUEUED"));
        }

        // ==============================
        // CHECK STATUS
        // ==============================
        @GetMapping("/status/{scanId}")
        public ResponseEntity<?> getStatus(@PathVariable("scanId") String scanId) {

                String status = scanService.getStatus(scanId);

                if ("NOT_FOUND".equals(status)) {
                        return ResponseEntity.notFound().build();
                }

                return ResponseEntity.ok(
                                Map.of(
                                                "scanId", scanId,
                                                "status", status));
        }

        // GET RESULT
        @GetMapping("/result/{scanId}")
        public ResponseEntity<?> getResult(
                        @PathVariable("scanId") String scanId) {

                return ResponseEntity.ok(
                                scanService.getResult(scanId));
        }
}