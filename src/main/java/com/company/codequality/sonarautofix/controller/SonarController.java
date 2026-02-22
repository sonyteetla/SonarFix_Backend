package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.IssueResponse;
import com.company.codequality.sonarautofix.service.ScanIssueService;
import com.company.codequality.sonarautofix.service.SonarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SonarController {

    private final SonarService sonarService;
    private final ScanIssueService sonarIssueService;

    public SonarController(SonarService sonarService,
                           ScanIssueService sonarIssueService) {
        this.sonarService = sonarService;
        this.sonarIssueService = sonarIssueService;
    }

   /* @GetMapping("/run-sonar")
    public String runSonar(@RequestParam String path) {
        return sonarService.runSonarScan(path);
    }
*/

    
}
