package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.Project;
import com.company.codequality.sonarautofix.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project registerProject(Project project) {
        List<Project> projects = projectRepository.findAll();
        Set<Integer> existingIds = projects.stream()
                .map(p -> {
                    try {
                        return Integer.parseInt(p.getId());
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                })
                .filter(id -> id > 0)
                .collect(Collectors.toSet());

        int nextId = 1;
        while (existingIds.contains(nextId)) {
            nextId++;
        }

        project.setId(String.valueOf(nextId));
        return projectRepository.save(project);
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Project getProjectById(String id) {
        return projectRepository.findById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found with id: " + id));
    }

    public void deleteProject(String id) {
        if (projectRepository.findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found with id: " + id);
        }
        projectRepository.deleteById(id);
    }
}
