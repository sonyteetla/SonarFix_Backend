package com.company.codequality.sonarautofix.model;

import java.util.List;

public class FixApplyRequest {

    private String scanId;
    private List<String> issueKeys;

    public FixApplyRequest() {
    }

    public FixApplyRequest(String scanId, List<String> issueKeys) {
        this.scanId = scanId;
        this.issueKeys = issueKeys;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public List<String> getIssueKeys() {
        return issueKeys;
    }

    public void setIssueKeys(List<String> issueKeys) {
        this.issueKeys = issueKeys;
    }
}