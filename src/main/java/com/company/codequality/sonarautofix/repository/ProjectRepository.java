package com.company.codequality.sonarautofix.repository;

import com.company.codequality.sonarautofix.model.Project;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.*;

@Repository
public class ProjectRepository {

    private static final String STORAGE_PATH = "data/projects.json";

    private final Map<String, Project> projectStore = new HashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @PostConstruct
    public void init() {
        loadProjects();
    }

    public synchronized void save(Project project) {

        projectStore.put(project.getProjectKey(), project);
        saveProjects();

    }

    public Project findByKey(String projectKey) {
        return projectStore.get(projectKey);
    }

    public List<Project> findAll() {
        return new ArrayList<>(projectStore.values());
    }

    private void saveProjects() {

        try {

            File file = new File(STORAGE_PATH);

            if (!file.getParentFile().exists())
                file.getParentFile().mkdirs();

            objectMapper.writeValue(file, projectStore);

        } catch (Exception e) {

            System.err.println("Failed to save projects: " + e.getMessage());

        }
    }

    private void loadProjects() {

        try {

            File file = new File(STORAGE_PATH);

            if (!file.exists()) return;

            Map<String, Project> loaded =
                    objectMapper.readValue(
                            file,
                            new TypeReference<Map<String, Project>>() {}
                    );

            projectStore.putAll(loaded);

            System.out.println("Loaded " + projectStore.size() + " projects");

        } catch (Exception e) {

            System.out.println("Invalid projects.json detected");

        }
    }
}