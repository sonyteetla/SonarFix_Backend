package com.company.codequality.sonarautofix.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;
@Service
public class WorkspaceResolverService {

    private final ProjectService projectService;

    public WorkspaceResolverService(ProjectService projectService) {
        this.projectService = projectService;
    }

    public String resolveWorkspacePath(String projectKey) {

        return projectService
                .getProject(projectKey)
                .getWorkspacePath();
    }
}