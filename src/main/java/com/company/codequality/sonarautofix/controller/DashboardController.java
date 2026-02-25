package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.dto.DashboardSummary;
import com.company.codequality.sonarautofix.dto.ModuleCount;
import com.company.codequality.sonarautofix.dto.SeverityCount;
import com.company.codequality.sonarautofix.model.Project;
import com.company.codequality.sonarautofix.service.DashboardService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin("*")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummary getSummary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/issues-by-severity")
    public List<SeverityCount> getIssuesBySeverity() {
        return dashboardService.getIssuesBySeverity();
    }

    @GetMapping("/issues-by-module")
    public List<ModuleCount> getIssuesByModule() {
        return dashboardService.getIssuesByModule();
    }

    @GetMapping("/recent-projects")
    public List<Project> getRecentProjects() {
        return dashboardService.getRecentProjects();
    }
}
