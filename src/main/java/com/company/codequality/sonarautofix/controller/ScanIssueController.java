package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.IssueResponse;
import com.company.codequality.sonarautofix.service.ScanIssueService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scan")
public class ScanIssueController {

    private final ScanIssueService scanIssueService;

    public ScanIssueController(ScanIssueService scanIssueService) {
        this.scanIssueService = scanIssueService;
    }

    @GetMapping("/{projectKey}/issues")
    public IssueResponse getIssues(
            @PathVariable String projectKey,
            @RequestParam(required = false) List<String> softwareQualities,
            @RequestParam(required = false) List<String> severities,
            @RequestParam(required = false) List<String> rules,
            @RequestParam(required = false) Boolean autoFixOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return scanIssueService.fetchIssues(
                projectKey,
                softwareQualities,
                severities,
                rules,
                autoFixOnly,
                page,
                pageSize
        );
    }
}