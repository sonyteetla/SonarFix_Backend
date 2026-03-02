package com.company.codequality.sonarautofix.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @JsonIgnore
    private String id;

    private String name;
    private String description;
    @JsonProperty("projectKey")
    public String getProjectKey() {
        return id;
    }
}