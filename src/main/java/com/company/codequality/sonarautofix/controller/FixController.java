package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.FixRequest;
import com.company.codequality.sonarautofix.service.AutoFixEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fix")
public class FixController {

    private final AutoFixEngine autoFixEngine;

    public FixController(AutoFixEngine autoFixEngine) {
        this.autoFixEngine = autoFixEngine;
    }

    @PostMapping
    public ResponseEntity<?> applyFixes(
            @RequestBody List<FixRequest> requests,
            @RequestParam String projectPath,
            @RequestParam String projectKey) {

        try {

            autoFixEngine.applyFixes(
                    requests,
                    projectPath,
                    projectKey
            );

            return ResponseEntity.ok(
                    "Fix(es) applied successfully. Re-scan started.");

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Auto fix failed: " + e.getMessage());
        }
    }
}