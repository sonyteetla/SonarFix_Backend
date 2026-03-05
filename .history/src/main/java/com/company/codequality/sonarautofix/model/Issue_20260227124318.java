package com.company.codequality.sonarautofix.model;

import lombok.*;
import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Issue {

    private String key;
    private String rule;
    private String severity;
    private String type;
    private String softwareQuality;
    private String message;
    private String filePath;
    private Integer line;
    private Integer startLine;
    private Integer endLine;
    private Integer startOffset;
    private Integer endOffset;
    private boolean supported;
    private boolean autoFixable;
    private FixType fixType;

    private List<ContentBlock> whyBlocks;

    private String nonCompliantExample;
    private String compliantExample;
}
