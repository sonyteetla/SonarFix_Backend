package com.company.codequality.sonarautofix.dto;

public class DifferenceResponse {

    private String fileName;
    private String originalContent;
    private String modifiedContent;

    public DifferenceResponse(String fileName,
                              String originalContent,
                              String modifiedContent) {
        this.fileName = fileName;
        this.originalContent = originalContent;
        this.modifiedContent = modifiedContent;
    }

    public String getFileName() {
        return fileName;
    }

    public String getOriginalContent() {
        return originalContent;
    }

    public String getModifiedContent() {
        return modifiedContent;
    }
}