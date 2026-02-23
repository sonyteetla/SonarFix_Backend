package com.company.codequality.sonarautofix.model;


public class ScanTask {

    private String scanId;
    private String projectPath;
    private String status;
    private String result;
    private String projectKey;

    public ScanTask(String scanId, String projectPath) {
        this.scanId = scanId;
        this.projectPath = projectPath;
        this.status = "QUEUED";
    }

    // Getters and setters

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

    public void setStatus(String status) {
        this.status = status;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }
}