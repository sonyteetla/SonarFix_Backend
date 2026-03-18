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
            @PathVariable("projectKey") String projectKey,
            @RequestParam(name = "softwareQualities", required = false) List<String> softwareQualities,
            @RequestParam(name = "severities", required = false) List<String> severities,
            @RequestParam(name = "rules", required = false) List<String> rules,
            @RequestParam(name = "autoFixOnly", required = false) Boolean autoFixOnly,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize
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
            @PathVariable("projectKey") String projectKey,
            @RequestParam(name = "softwareQualities", required = false) List<String> softwareQualities,
            @RequestParam(name = "severities", required = false) List<String> severities,
            @RequestParam(name = "rules", required = false) List<String> rules,
            @RequestParam(name = "autoFixOnly", required = false) Boolean autoFixOnly
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