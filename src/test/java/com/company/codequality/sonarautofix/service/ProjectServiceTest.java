package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.Project;
import com.company.codequality.sonarautofix.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    private ProjectRepository projectRepository;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectRepository = Mockito.mock(ProjectRepository.class);
        projectService = new ProjectService(projectRepository);
    }

    @Test
    void testRegisterProjectSequentialId() {
        Project p1 = Project.builder().projectKey("P1").build();
        Project p2 = Project.builder().projectKey("P2").build();
        Project p3 = Project.builder().projectKey("P3").build();

        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findAll()).thenReturn(new java.util.ArrayList<>());

        Project saved1 = projectService.registerProject(p1);

        when(projectRepository.findAll()).thenReturn(java.util.List.of(saved1));
        Project saved2 = projectService.registerProject(p2);

        when(projectRepository.findAll()).thenReturn(java.util.List.of(saved1, saved2));
        Project saved3 = projectService.registerProject(p3);

        assertEquals("1", saved1.getId());
        assertEquals("2", saved2.getId());
        assertEquals("3", saved3.getId());
    }

    @Test
    void testRegisterProjectReuseId() {
        Project p1 = Project.builder().projectKey("P1").build();
        Project p2 = Project.builder().projectKey("P2").build();
        Project p3 = Project.builder().projectKey("P3").build();

        // Simulate behavior where findAll returns the current list
        java.util.List<Project> store = new java.util.ArrayList<>();
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            store.add(p);
            return p;
        });
        when(projectRepository.findAll()).thenAnswer(invocation -> new java.util.ArrayList<>(store));
        when(projectRepository.findById(any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return store.stream().filter(p -> p.getId().equals(id)).findFirst();
        });

        Project saved1 = projectService.registerProject(p1);
        Project saved2 = projectService.registerProject(p2);

        assertEquals("1", saved1.getId());
        assertEquals("2", saved2.getId());

        // Delete project 1 and register a new one
        store.removeIf(p -> p.getId().equals("1"));

        Project saved3 = projectService.registerProject(p3);
        assertEquals("1", saved3.getId()); // Should reuse 1
    }
}
