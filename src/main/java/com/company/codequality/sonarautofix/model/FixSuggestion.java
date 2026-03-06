package com.company.codequality.sonarautofix.model;

public class FixSuggestion {

    private String file;
    private Integer line;
    private String ruleId;
    private String message;
    private String suggestedCode;

    public FixSuggestion() {
    }

    public FixSuggestion(String file, Integer line,
            String ruleId, String message,
            String suggestedCode) {
        this.file = file;
        this.line = line;
        this.ruleId = ruleId;
        this.message = message;
        this.suggestedCode = suggestedCode;
    }

    public String getFile() {
        return file;
    }

    public Integer getLine() {
        return line;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestedCode() {
        return suggestedCode;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public void setLine(Integer line) {
        this.line = line;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setSuggestedCode(String suggestedCode) {
        this.suggestedCode = suggestedCode;
    }
}