package com.company.codequality.sonarautofix.model;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileIssueGroup {

    private String file;
    private List<Issue> issues;
}