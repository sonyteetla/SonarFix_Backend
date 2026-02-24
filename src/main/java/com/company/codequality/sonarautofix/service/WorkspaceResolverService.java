package com.company.codequality.sonarautofix.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

@Service
public class WorkspaceResolverService {

    private static final String WORKSPACE = "C:/sonar-workspace/";

    public String resolveWorkspacePath(String projectKey) {

        try (Stream<Path> folders = Files.list(Paths.get(WORKSPACE))) {

            return folders
                    .filter(Files::isDirectory)
                    .filter(folder -> hasMatchingKey(folder, projectKey))
                    .map(Path::toString)
                    .findFirst()
                    .orElseThrow(() ->
                            new RuntimeException("Project not found for key: " + projectKey));

        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve workspace", e);
        }
    }

    private boolean hasMatchingKey(Path folder, String projectKey) {

        Path metadataFile = folder.resolve(".project-key");

        if (!Files.exists(metadataFile)) {
            return false;
        }

        try {
            String storedKey = Files.readString(metadataFile).trim();
            return storedKey.equals(projectKey);
        } catch (IOException e) {
            return false;
        }
    }
}