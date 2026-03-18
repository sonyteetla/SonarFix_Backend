package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.Project;
import com.company.codequality.sonarautofix.service.SonarProjectsFetchService;
import com.company.codequality.sonarautofix.service.SonarProjectsDeleteService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sonar")
@CrossOrigin
public class SonarProjectsFetchController {

    private final SonarProjectsFetchService sonarProjectsFetch;
    private final SonarProjectsDeleteService sonarProjectDeleteService;

    public SonarProjectsFetchController(
            SonarProjectsFetchService sonarProjectsFetch,
            SonarProjectsDeleteService sonarProjectDeleteService
    ) {
        this.sonarProjectsFetch = sonarProjectsFetch;
        this.sonarProjectDeleteService = sonarProjectDeleteService;
    }

    @GetMapping("/projects")
    public List<Project> fetchSonarProjects() {
        return sonarProjectsFetch.fetchAllProjects();
    }

    @DeleteMapping("/projects/{projectKey}")
    public String deleteProject(@PathVariable("projectKey") String projectKey) {
        return sonarProjectDeleteService.deleteProject(projectKey);
    }
}