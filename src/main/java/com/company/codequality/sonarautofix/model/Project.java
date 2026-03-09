package com.company.codequality.sonarautofix.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    private String projectKey;
    private String name;
    private String description;
    private String workspacePath;
    private long createdAt;

}