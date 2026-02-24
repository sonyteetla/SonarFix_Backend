package com.company.codequality.sonarautofix.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MappedIssue {

    private String ruleId;
    private String file;
    private int line;

    private String title;
    private String severity;
    private String category;

    private boolean autoFixable;
    private String fixType;
}
