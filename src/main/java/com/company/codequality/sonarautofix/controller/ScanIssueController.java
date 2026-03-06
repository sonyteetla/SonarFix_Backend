package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.IssueResponse;
import com.company.codequality.sonarautofix.service.ScanIssueService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scan")
@CrossOrigin(origins = "*")
public class ScanIssueController {

    private final ScanIssueService scanIssueService;

    public ScanIssueController(ScanIssueService scanIssueService) {
        this.scanIssueService = scanIssueService;
    }

    // ================= PAGINATED ISSUES =================

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

    // ================= ALL ISSUES (FOR VIEWER) =================

    @GetMapping("/{projectKey}/issues/all")
    public IssueResponse getAllIssues(
            @PathVariable String projectKey,
            @RequestParam(required = false) List<String> softwareQualities,
            @RequestParam(required = false) List<String> severities,
            @RequestParam(required = false) List<String> rules,
            @RequestParam(required = false) Boolean autoFixOnly
    ) {

        return scanIssueService.fetchAllIssuesForViewer(
                projectKey,
                softwareQualities,
                severities,
                rules,
                autoFixOnly
        );
    }
}