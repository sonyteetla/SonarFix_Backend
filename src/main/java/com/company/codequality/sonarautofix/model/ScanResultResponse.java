package com.company.codequality.sonarautofix.model;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScanResultResponse {

    private String scanId;
    private String projectKey;
    private String status;

    private int totalIssues;
    private int autoFixableCount;

    private List<MappedIssue> issues;
}
