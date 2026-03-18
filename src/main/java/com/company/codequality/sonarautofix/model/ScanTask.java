package com.company.codequality.sonarautofix.model;

import java.time.LocalDateTime;
import java.util.*;

public class ScanTask {

    private String scanId;
    private String projectPath;
    private String status;
    private String result;
    private String projectKey;
    private String fixedPath;
    private LocalDateTime createdAt;

    private List<MappedIssue> mappedIssues = new ArrayList<>();

    // Suggestions
    private List<FixSuggestion> suggestions = new ArrayList<>();

    // Build Log
    private String buildLog;

    // Execution Summary
    private Map<String, Integer> fixExecutionReport = new HashMap<>();
    private int totalFixesApplied;

    // Detailed Fix Reports
    private List<FixExecutionReport> fixReports = new ArrayList<>();

    // ================= CONSTRUCTORS =================

    public ScanTask() {
        this.createdAt = LocalDateTime.now();
        this.status = "QUEUED";
    }

    public ScanTask(String scanId, String projectPath) {
        this.scanId = scanId;
        this.projectPath = projectPath;
        this.status = "QUEUED";
        this.createdAt = LocalDateTime.now();
    }

    // ================= GETTERS =================

    public String getScanId() { return scanId; }

    public String getProjectPath() { return projectPath; }

    public String getStatus() { return status; }

    public String getResult() { return result; }

    public String getProjectKey() { return projectKey; }

    public String getFixedPath() { return fixedPath; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<MappedIssue> getMappedIssues() { return mappedIssues; }

    public List<FixSuggestion> getSuggestions() { return suggestions; }

    public String getBuildLog() { return buildLog; }

    public Map<String, Integer> getFixExecutionReport() { return fixExecutionReport; }

    public int getTotalFixesApplied() { return totalFixesApplied; }

    public List<FixExecutionReport> getFixReports() { return fixReports; }

    // ================= SETTERS =================

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public void setFixedPath(String fixedPath) {
        this.fixedPath = fixedPath;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public void setMappedIssues(List<MappedIssue> mappedIssues) {
        this.mappedIssues = mappedIssues != null ? mappedIssues : new ArrayList<>();
    }

    public void setSuggestions(List<FixSuggestion> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }

    public void setBuildLog(String buildLog) {
        this.buildLog = buildLog;
    }

    public void setFixExecutionReport(Map<String, Integer> report) {
        this.fixExecutionReport = report != null ? report : new HashMap<>();
    }

    public void setTotalFixesApplied(int total) {
        this.totalFixesApplied = total;
    }

    public void setFixReports(List<FixExecutionReport> reports) {
        this.fixReports = reports != null ? reports : new ArrayList<>();
    }

    // ================= HELPERS =================

    public void addSuggestion(FixSuggestion suggestion) {
        if (suggestion != null) {
            suggestions.add(suggestion);
        }
    }

    public void addFixReport(FixExecutionReport report) {
        if (report != null) {
            fixReports.add(report);
        }
    }
}