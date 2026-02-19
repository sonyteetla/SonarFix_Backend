package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.service.SonarIssueService;
import com.company.codequality.sonarautofix.service.SonarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SonarController {

    private final SonarService sonarService;
    private final SonarIssueService sonarIssueService;

    public SonarController(SonarService sonarService,
                           SonarIssueService sonarIssueService) {
        this.sonarService = sonarService;
        this.sonarIssueService = sonarIssueService;
    }

    @GetMapping("/run-sonar")
    public String runSonar() {
        return sonarService.runSonarScan();
    }

    @GetMapping("/issues")
    public String getIssues() {
        return sonarIssueService.fetchIssues();
    }
}
