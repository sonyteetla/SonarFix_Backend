package com.company.codequality.sonarautofix.model;

public class FixExecutionReport {

    private String ruleId;
    private String filePath;
    private int line;

    private String beforeCode;
    private String afterCode;

    private boolean success;
    private String message;

    public FixExecutionReport() {}

    public FixExecutionReport(String ruleId,
                              String filePath,
                              int line,
                              String beforeCode,
                              String afterCode,
                              boolean success,
                              String message) {
        this.ruleId = ruleId;
        this.filePath = filePath;
        this.line = line;
        this.beforeCode = beforeCode;
        this.afterCode = afterCode;
        this.success = success;
        this.message = message;
    }

    public String getRuleId() { return ruleId; }
    public String getFilePath() { return filePath; }
    public int getLine() { return line; }
    public String getBeforeCode() { return beforeCode; }
    public String getAfterCode() { return afterCode; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }

    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setLine(int line) { this.line = line; }
    public void setBeforeCode(String beforeCode) { this.beforeCode = beforeCode; }
    public void setAfterCode(String afterCode) { this.afterCode = afterCode; }
    public void setSuccess(boolean success) { this.success = success; }
    public void setMessage(String message) { this.message = message; }
}