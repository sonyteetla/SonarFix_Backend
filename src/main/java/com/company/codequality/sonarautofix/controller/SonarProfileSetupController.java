package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.service.SonarProfileSetupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quality/sonar")
public class SonarProfileSetupController {

    private final SonarProfileSetupService sonarProfileSetupService;

    public SonarProfileSetupController(SonarProfileSetupService sonarProfileSetupService) {
        this.sonarProfileSetupService = sonarProfileSetupService;
    }

    @PostMapping("/setup-profile")
    public ResponseEntity<?> setupQualityProfile() {

        sonarProfileSetupService.setupIfNotExists();

        return ResponseEntity.ok()
                .body("AutoFix-25-Java profile created and set as default (if not already existing).");
    }
}