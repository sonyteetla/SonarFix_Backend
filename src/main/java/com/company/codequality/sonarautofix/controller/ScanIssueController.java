package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.IssueResponse;
import com.company.codequality.sonarautofix.service.ScanIssueService;
import com.company.codequality.sonarautofix.service.RuleEngineService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scan")
public class ScanIssueController {

    private final ScanIssueService sonarIssueService;
    private final RuleEngineService ruleEngineService;

    public ScanIssueController(
            ScanIssueService sonarIssueService,
            RuleEngineService ruleEngineService
    ) {
        this.sonarIssueService = sonarIssueService;
        this.ruleEngineService = ruleEngineService;
    }

    @GetMapping("/{projectKey}/issues")
    public IssueResponse getIssues(
            @PathVariable String projectKey,
            @RequestParam(required = false) List<String> softwareQualities,
            @RequestParam(required = false) List<String> severities,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize
    ) {

        // Fetch Sonar issues
        IssueResponse rawResponse =
                sonarIssueService.fetchIssues(
                        projectKey,
                        softwareQualities,
                        severities,
                        statuses,
                        tags,
                        page,
                        pageSize
                );

        // Apply rule mapping
        return ruleEngineService.applyRuleMapping(rawResponse);
    }
}