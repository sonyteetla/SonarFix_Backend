package com.company.codequality.sonarautofix.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
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
