package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileContentService {

    private final WorkspaceResolverService resolverService;

    public FileContentService(WorkspaceResolverService resolverService) {
        this.resolverService = resolverService;
    }

    public List<String> getFileContent(String projectKey,
                                       String relativePath) {

        try {

            String workspacePath =
                    resolverService.resolveWorkspacePath(projectKey);

            Path basePath = Paths.get(workspacePath);
            Path fullPath = basePath.resolve(relativePath).normalize();

            if (!fullPath.startsWith(basePath)) {
                throw new RuntimeException("Invalid file path access");
            }

            if (!Files.exists(fullPath)) {
                throw new RuntimeException("File not found: " + fullPath);
            }

            return Files.readAllLines(fullPath);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }
}