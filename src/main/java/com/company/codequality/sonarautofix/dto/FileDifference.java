package com.company.codequality.sonarautofix.dto;

import java.util.List;

public class FileDifference {

    private String fileName;              // Just the file name (e.g., SonarService.java)
    private String relativePath;          // Full relative path
    private List<LineDifference> differences;  // List of differences in this file

    public FileDifference(String fileName, String relativePath, List<LineDifference> differences) {
        this.fileName = fileName;
        this.relativePath = relativePath;
        this.differences = differences;
    }

    // Getters
    public String getFileName() {
        return fileName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public List<LineDifference> getDifferences() {
        return differences;
    }
}