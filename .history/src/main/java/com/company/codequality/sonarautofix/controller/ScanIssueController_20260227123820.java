package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.IssueResponse;
import com.company.codequality.sonarautofix.service.ScanIssueService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scan")
@CrossOrigin("*")
public class ScanIssueController {

    private final ScanIssueService scanIssueService;

    public ScanIssueController(ScanIssueService scanIssueService) {
        this.scanIssueService = scanIssueService;
    }

    @GetMapping("/{projectKey}/issues")
    public IssueResponse getIssues(
            @PathVariable("projectKey") String projectKey,
            @RequestParam(value = "softwareQualities", required = false) List<String> softwareQualities,
            @RequestParam(value = "severities", required = false) List<String> severities,
            @RequestParam(value = "rules", required = false) List<String> rules,
            @RequestParam(value = "autoFixOnly", required = false) Boolean autoFixOnly,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return scanIssueService.fetchIssues(
                projectKey,
                softwareQualities,
                severities,
                rules,
                autoFixOnly,
                page,
                pageSize);
    }
}