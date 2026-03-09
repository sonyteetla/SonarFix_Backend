package com.company.codequality.sonarautofix.model;

public class DiffLine {

    private int lineNumber;
    private String originalLine;
    private String modifiedLine;
    private String type;

    public DiffLine(int lineNumber,
                    String originalLine,
                    String modifiedLine,
                    String type) {

        this.lineNumber = lineNumber;
        this.originalLine = originalLine;
        this.modifiedLine = modifiedLine;
        this.type = type;
    }

    public int getLineNumber() { return lineNumber; }
    public String getOriginalLine() { return originalLine; }
    public String getModifiedLine() { return modifiedLine; }
    public String getType() { return type; }
}