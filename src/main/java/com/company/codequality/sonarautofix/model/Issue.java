package com.company.codequality.sonarautofix.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issue {

    private String key;
    private String rule;
    private String severity;
    private String type;
    private String softwareQuality;
    private String message;
    private String filePath;
    private Integer line;
    private boolean supported;
    private boolean autoFixable;
    private FixType fixType;
}