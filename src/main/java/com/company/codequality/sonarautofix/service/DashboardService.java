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

    public DashboardService(ProjectService projectService,
                            ScanService scanService,
                            FixRecordRepository fixRecordRepository) {
        this.projectService = projectService;
        this.scanService = scanService;
        this.fixRecordRepository = fixRecordRepository;
    }

    // ================= SUMMARY =================
    public DashboardSummary getSummary() {

        List<Project> projects = projectService.getAllProjects();
        Collection<ScanTask> latestScans = getLatestScansPerProject();

        long totalIssues = latestScans.stream()
                .filter(s -> s.getMappedIssues() != null)
                .flatMap(s -> s.getMappedIssues().stream())
                .count();

        long totalFixed = fixRecordRepository.count();

        return DashboardSummary.builder()
                .totalProjects(projects.size())
                .totalScans(getAllScansSafe().size())
                .totalIssues(totalIssues)
                .totalFixed(totalFixed)
                .build();
    }

    // ================= ISSUES BY SEVERITY =================
    public List<SeverityCount> getIssuesBySeverity() {

        Collection<ScanTask> latestScans = getLatestScansPerProject();

        Map<String, Long> counts = latestScans.stream()
                .filter(s -> s.getMappedIssues() != null)
                .flatMap(s -> s.getMappedIssues().stream())
                .collect(Collectors.groupingBy(
                        issue -> issue.getSeverity() == null ? "UNKNOWN" : issue.getSeverity(),
                        Collectors.counting()
                ));

        return counts.entrySet().stream()
                .map(e -> new SeverityCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    // ================= ISSUES BY MODULE =================
    public List<ModuleCount> getIssuesByModule() {

        Collection<ScanTask> latestScans = getLatestScansPerProject();

        Map<String, Long> counts = latestScans.stream()
                .filter(s -> s.getMappedIssues() != null)
                .flatMap(s -> s.getMappedIssues().stream())
                .collect(Collectors.groupingBy(issue -> {

                    String file = issue.getFile();
                    if (file == null || file.isBlank()) return "Unknown";

                    file = file.replace("\\", "/");
                    String[] parts = file.split("/");

                    if (parts.length > 1) {
                        return parts[parts.length - 2]; // parent folder = module
                    }

                    return "root";

                }, Collectors.counting()));

        return counts.entrySet().stream()
                .map(e -> new ModuleCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    // ================= LATEST SCANS PER PROJECT =================
    private Collection<ScanTask> getLatestScansPerProject() {

        List<ScanTask> scans = getAllScansSafe();
        Map<String, ScanTask> latest = new HashMap<>();

        for (ScanTask s : scans) {

            if (s.getMappedIssues() == null) continue;

            latest.merge(
                    s.getProjectKey(),
                    s,
                    (existing, replacement) ->
                            replacement.getCreatedAt().isAfter(existing.getCreatedAt())
                                    ? replacement
                                    : existing
            );
        }

        return latest.values();
    }

    // ================= SAFE ACCESS TO ALL SCANS =================
    private List<ScanTask> getAllScansSafe() {
        try {
            return scanService.getAllScans(); // If method exists later
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ================= RECENT PROJECTS =================
    public List<Project> getRecentProjects() {

        List<Project> projects = projectService.getAllProjects();
        List<Project> sorted = new ArrayList<>(projects);
        Collections.reverse(sorted);

        return sorted.stream().limit(5).collect(Collectors.toList());
    }
}