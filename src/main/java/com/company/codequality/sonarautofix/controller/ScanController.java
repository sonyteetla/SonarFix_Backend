package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.service.ScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    // 1️⃣ Start Scan
    @PostMapping("/start")
    public ResponseEntity<?> startScan(@RequestParam String projectPath) {
        String scanId = scanService.startScan(projectPath);
        return ResponseEntity.ok("Scan started. scanId = " + scanId);
    }

    // 2️⃣ Check Status
    @GetMapping("/status/{scanId}")
    public ResponseEntity<?> getStatus(@PathVariable String scanId) {
        return ResponseEntity.ok(scanService.getStatus(scanId));
    }

    // 3️⃣ Get Result
    @GetMapping("/result/{scanId}")
    public ResponseEntity<?> getResult(@PathVariable String scanId) {
        return ResponseEntity.ok(scanService.getResult(scanId));
    }
}
