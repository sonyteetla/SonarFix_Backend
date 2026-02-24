package com.company.codequality.sonarautofix.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Issue {

    private String key;
    private String rule;
    private String severity;
    private String type;
    private String softwareQuality;
    private String message;
    private String description;
    private String filePath;
    private Integer line;

    // required for exact highlight
    private Integer startLine;
    private Integer endLine;
    private Integer startOffset;
    private Integer endOffset;

    private boolean supported;
    private boolean autoFixable;
    private FixType fixType;
}