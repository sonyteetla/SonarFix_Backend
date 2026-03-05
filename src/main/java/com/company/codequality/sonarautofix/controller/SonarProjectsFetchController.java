package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.Project;
import com.company.codequality.sonarautofix.service.SonarProjectsFetchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sonar")
public class SonarProjectsFetchController {

    private final SonarProjectsFetchService sonarProjectsFetch;

    public SonarProjectsFetchController(SonarProjectsFetchService sonarProjectsFetch) {
        this.sonarProjectsFetch = sonarProjectsFetch;
    }

    @GetMapping("/projects")
    public List<Project> fetchSonarProjects() {
        return sonarProjectsFetch.fetchAllProjects();
    }
}