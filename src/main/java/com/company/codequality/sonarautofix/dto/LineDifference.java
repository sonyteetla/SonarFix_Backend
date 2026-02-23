package com.company.codequality.sonarautofix.dto;

public class LineDifference {

    private int lineNumber;        // Line number in the file
    private String originalLine;   // Original content
    private String modifiedLine;   // Fixed content

    public LineDifference(int lineNumber, String originalLine, String modifiedLine) {
        this.lineNumber = lineNumber;
        this.originalLine = originalLine;
        this.modifiedLine = modifiedLine;
    }

    // Getters
    public int getLineNumber() {
        return lineNumber;
    }

    public String getOriginalLine() {
        return originalLine;
    }

    public String getModifiedLine() {
        return modifiedLine;
    }
}