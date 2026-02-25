package com.company.codequality.sonarautofix.model;

import java.util.*;

public class ScanTask {

    private String scanId;
    private String projectPath;
    private String status;
    private String result;
    private String projectKey;

    private List<MappedIssue> mappedIssues;

    // Suggestions
    private List<FixSuggestion> suggestions = new ArrayList<>();

    // Build Log (NEW)
    private String buildLog;

    // Execution Report (NEW)
    private Map<String, Integer> fixExecutionReport = new HashMap<>();
    private int totalFixesApplied;

    public ScanTask(String scanId, String projectPath) {
        this.scanId = scanId;
        this.projectPath = projectPath;
        this.status = "QUEUED";
    }

    // ================= GETTERS =================

    public String getScanId() { return scanId; }
    public String getProjectPath() { return projectPath; }
    public String getStatus() { return status; }
    public String getResult() { return result; }
    public String getProjectKey() { return projectKey; }
    public List<MappedIssue> getMappedIssues() { return mappedIssues; }
    public List<FixSuggestion> getSuggestions() { return suggestions; }

    public String getBuildLog() { return buildLog; }

    public Map<String, Integer> getFixExecutionReport() {
        return fixExecutionReport;
    }

    public int getTotalFixesApplied() {
        return totalFixesApplied;
    }

    // ================= SETTERS =================

    public void setStatus(String status) { this.status = status; }
    public void setResult(String result) { this.result = result; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public void setMappedIssues(List<MappedIssue> mappedIssues) { this.mappedIssues = mappedIssues; }
    public void setBuildLog(String buildLog) { this.buildLog = buildLog; }

    public void setFixExecutionReport(Map<String, Integer> report) {
        this.fixExecutionReport = report;
    }

    public void setTotalFixesApplied(int total) {
        this.totalFixesApplied = total;
    }

    // ================= SUGGESTIONS =================

    public void addSuggestion(FixSuggestion suggestion) {
        if (this.suggestions == null) {
            this.suggestions = new ArrayList<>();
        }
        this.suggestions.add(suggestion);
    }

    public void setSuggestions(List<FixSuggestion> suggestions) {
        this.suggestions = (suggestions == null) ? new ArrayList<>() : suggestions;
    }
}