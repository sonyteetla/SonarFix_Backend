package com.company.codequality.sonarautofix.repository;

import com.company.codequality.sonarautofix.model.Project;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProjectRepository {
    private static final String STORAGE_PATH = "data/projects.json";
    private final Map<String, Project> projects = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public ProjectRepository() {
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        loadProjects();
    }

    public Project save(Project project) {
        projects.put(project.getId(), project);
        saveProjects();
        return project;
    }

    public List<Project> findAll() {
        return new ArrayList<>(projects.values());
    }

    public Optional<Project> findById(String id) {
        return Optional.ofNullable(projects.get(id));
    }

    public void deleteById(String id) {
        projects.remove(id);
        saveProjects();
    }

    private synchronized void saveProjects() {
        try {
            File file = new File(STORAGE_PATH);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            objectMapper.writeValue(file, projects);
        } catch (IOException e) {
            System.err.println("Failed to save projects: " + e.getMessage());
        }
    }

    private void loadProjects() {
        try {
            File file = new File(STORAGE_PATH);
            if (file.exists()) {
                Map<String, Project> loaded = objectMapper.readValue(file, new TypeReference<Map<String, Project>>() {
                });
                projects.putAll(loaded);
                System.out.println("Loaded " + projects.size() + " projects from " + STORAGE_PATH);
            }
        } catch (IOException e) {
            System.err.println("Failed to load projects: " + e.getMessage());
        }
    }
}
