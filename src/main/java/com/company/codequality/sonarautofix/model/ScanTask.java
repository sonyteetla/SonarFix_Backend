package com.company.codequality.sonarautofix.model;

import java.util.List;

public class ScanTask {

    private String scanId;
    private String projectPath;
    private String status;
    private String result;
    private String projectKey;

    // ✅ Store mapped issues for AutoFix + JSON response
    private List<MappedIssue> mappedIssues;

    public ScanTask(String scanId, String projectPath) {
        this.scanId = scanId;
        this.projectPath = projectPath;
        this.status = "QUEUED";
    }

    // Getters

    public String getScanId() {
        return scanId;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public List<MappedIssue> getMappedIssues() {
        return mappedIssues;
    }

    // Setters

    public void setStatus(String status) {
        this.status = status;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public void setMappedIssues(List<MappedIssue> mappedIssues) {
        this.mappedIssues = mappedIssues;
    }
}
