package com.company.codequality.sonarautofix.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor          // ✅ REQUIRED for Jackson
@AllArgsConstructor
@Builder
public class MappedIssue {
	 private String key;
    private String ruleId;
    private String file;
    private int line;

    private String title;
    private String severity;
    private String category;
   
    private boolean autoFixable;
    private String fixType;
}