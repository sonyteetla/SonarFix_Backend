package com.company.codequality.sonarautofix.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {
    private String id;

    @JsonProperty("projectKey")
    @JsonAlias({ "project Key", "projectName" })
    private String projectKey;

    private String description;
}
