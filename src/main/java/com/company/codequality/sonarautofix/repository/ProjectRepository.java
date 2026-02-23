package com.company.codequality.sonarautofix.repository;

import com.company.codequality.sonarautofix.model.Project;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProjectRepository {
    private final Map<String, Project> projects = new ConcurrentHashMap<>();

    public Project save(Project project) {
        projects.put(project.getId(), project);
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
    }
}
