package com.company.codequality.sonarautofix.model;

public class DetectedIssue {

    private String ruleId;
    private String title;
    private String filePath;
    private int line;
    private String severity;
    private boolean autofixSupported;

    public DetectedIssue(String ruleId, String title, String filePath,
                         int line, String severity, boolean autofixSupported) {
        this.ruleId = ruleId;
        this.title = title;
        this.filePath = filePath;
        this.line = line;
        this.severity = severity;
        this.autofixSupported = autofixSupported;
    }

    // getters
    public String getRuleId() { return ruleId; }
    public String getTitle() { return title; }
    public String getFilePath() { return filePath; }
    public int getLine() { return line; }
    public String getSeverity() { return severity; }
    public boolean isAutofixSupported() { return autofixSupported; }
}
