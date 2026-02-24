package com.company.codequality.sonarautofix.model;

public class SonarIssue {

    private String rule;
    private String component;
    private Integer line;

    public SonarIssue() {}

    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }

    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }

    public Integer getLine() { return line; }
    public void setLine(Integer line) { this.line = line; }
}
