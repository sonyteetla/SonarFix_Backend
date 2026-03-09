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

    	int totalProjects = projectService.getSonarProjectCount();
        Collection<ScanTask> latestScans = getLatestScansPerProject();

        long totalIssues = latestScans.stream()
                .flatMap(s -> s.getMappedIssues().stream())
                .count();

        long totalFixed = fixRecordRepository.count();

        return DashboardSummary.builder()
                .totalProjects(totalProjects)
                .totalScans(getAllScansSafe().size())
                .totalIssues(totalIssues)
                .totalFixed(totalFixed)
                .build();
    }

    // ================= ISSUES BY SEVERITY =================

    public List<SeverityCount> getIssuesBySeverity() {

        Collection<ScanTask> latestScans = getLatestScansPerProject();

        Map<String, Long> counts = latestScans.stream()
                .flatMap(s -> s.getMappedIssues().stream())
                .collect(Collectors.groupingBy(
                        issue -> issue.getSeverity() == null
                                ? "UNKNOWN"
                                : issue.getSeverity(),
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
                .flatMap(s -> s.getMappedIssues().stream())
                .collect(Collectors.groupingBy(issue -> {

                    String file = issue.getFile();

                    if (file == null || file.isBlank()) {
                        return "Unknown";
                    }

                    file = file.replace("\\", "/");
                    String[] parts = file.split("/");

                    if (parts.length > 1) {
                        return parts[parts.length - 2];
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

        for (ScanTask scan : scans) {

            if (scan == null) continue;
            if (scan.getProjectKey() == null) continue;
            if (scan.getCreatedAt() == null) continue;

            ScanTask existing = latest.get(scan.getProjectKey());

            if (existing == null) {
                latest.put(scan.getProjectKey(), scan);
                continue;
            }

            if (existing.getCreatedAt() == null ||
                    scan.getCreatedAt().isAfter(existing.getCreatedAt())) {

                latest.put(scan.getProjectKey(), scan);
            }
        }

        return latest.values();
    }

    // ================= SAFE ACCESS =================

    private List<ScanTask> getAllScansSafe() {

        try {
            return scanService.getAllScans();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ================= RECENT PROJECTS =================

    public List<Project> getRecentProjects() {

        List<Project> projects = projectService.getAllProjects();

        return projects.stream()
                .sorted(Comparator.comparing(Project::getProjectKey).reversed())
                .limit(5)
                .collect(Collectors.toList());
    }
}