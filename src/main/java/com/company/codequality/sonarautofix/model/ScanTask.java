package com.company.codequality.sonarautofix.model;

public class ScanTask {

    private String scanId;
    private String projectPath;
    private String status;     // QUEUED, RUNNING, COMPLETED, FAILED
    private String result;

    public ScanTask(String scanId, String projectPath) {
        this.scanId = scanId;
        this.projectPath = projectPath;
        this.status = "QUEUED";
    }

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

    public void setStatus(String status) {
        this.status = status;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
