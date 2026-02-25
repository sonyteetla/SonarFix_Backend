package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.dto.DashboardSummary;
import com.company.codequality.sonarautofix.dto.ModuleCount;
import com.company.codequality.sonarautofix.dto.SeverityCount;
import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.repository.FixRecordRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ProjectService projectService;
    private final ScanService scanService;
    private final FixRecordRepository fixRecordRepository;

    public DashboardService(ProjectService projectService, ScanService scanService,
            FixRecordRepository fixRecordRepository) {
        this.projectService = projectService;
        this.scanService = scanService;
        this.fixRecordRepository = fixRecordRepository;
    }

    public DashboardSummary getSummary() {
        List<Project> projects = projectService.getAllProjects();
        Collection<ScanTask> latestScans = getLatestScansPerProject();

        long totalIssues = latestScans.stream()
                .flatMap(s -> s.getMappedIssues().stream())
                .count();

        // Use the count of actually applied fixes from the repository
        long totalFixed = fixRecordRepository.count();

        return DashboardSummary.builder()
                .totalProjects(projects.size())
                .totalScans(scanService.getAllScans().size())
                .totalIssues(totalIssues)
                .totalFixed(totalFixed)
                .build();
    }

    public List<SeverityCount> getIssuesBySeverity() {
        Collection<ScanTask> latestScans = getLatestScansPerProject();

        Map<String, Long> counts = latestScans.stream()
                .flatMap(s -> s.getMappedIssues().stream())
                .collect(Collectors.groupingBy(MappedIssue::getSeverity, Collectors.counting()));

        return counts.entrySet().stream()
                .map(e -> new SeverityCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public List<ModuleCount> getIssuesByModule() {
        Collection<ScanTask> latestScans = getLatestScansPerProject();

        Map<String, Long> counts = latestScans.stream()
                .flatMap(s -> s.getMappedIssues().stream())
                .collect(Collectors.groupingBy(issue -> {
                    String file = issue.getFile();
                    if (file == null || file.isEmpty())
                        return "Unknown";

                    // Normalize path
                    file = file.replace("\\", "/");

                    // Try to find the most meaningful "module" or "package" component
                    String[] parts = file.split("/");

                    // If it's a standard Maven/Gradle project, skip the common prefix
                    int startIdx = 0;
                    if (parts.length > 3 && parts[0].equals("src") && parts[1].equals("main")
                            && parts[2].equals("java")) {
                        startIdx = 3;
                    }

                    // Look for common layer names or just get the parent folder
                    if (parts.length > 1) {
                        // If we have many parts, return the leaf folder (the layer)
                        return parts[parts.length - 2];
                    }

                    return "root";
                }, Collectors.counting()));

        return counts.entrySet().stream()
                .map(e -> new ModuleCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private Collection<ScanTask> getLatestScansPerProject() {
        List<ScanTask> scans = scanService.getAllScans();
        Map<String, ScanTask> latestScans = new HashMap<>();
        for (ScanTask s : scans) {
            if (s.getMappedIssues() == null)
                continue;
            latestScans.merge(s.getProjectKey(), s,
                    (existing, replacement) -> replacement.getCreatedAt().isAfter(existing.getCreatedAt()) ? replacement
                            : existing);
        }
        return latestScans.values();
    }

    public List<Project> getRecentProjects() {
        List<Project> projects = projectService.getAllProjects();
        // Since we don't have createdAt, we just return the last 5 added (assuming ID
        // order or just reverse list)
        List<Project> sorted = new ArrayList<>(projects);
        Collections.reverse(sorted);
        return sorted.stream().limit(5).collect(Collectors.toList());
    }
}
