package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.Project;
import com.company.codequality.sonarautofix.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${sonar.host.url}")
    private String sonarUrl;

    @Value("${sonar.token}")
    private String sonarToken;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    // ================= REGISTER PROJECT =================

    public void registerProject(String projectKey, String workspacePath) {

        Project project =
                Project.builder()
                        .projectKey(projectKey)
                        .workspacePath(workspacePath)
                        .name(projectKey)
                        .description("Uploaded Project")
                        .createdAt(System.currentTimeMillis())
                        .build();

        projectRepository.save(project);
    }

    // ================= GET PROJECT =================

    public Project getProject(String projectKey) {

        Project project = projectRepository.findByKey(projectKey);

        if (project == null)
            throw new RuntimeException("Project not found: " + projectKey);

        return project;
    }

    // ================= LOCAL PROJECTS =================

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    // ================= SONAR PROJECT COUNT =================

    public int getSonarProjectCount() {

        try {

            String url = sonarUrl + "/api/projects/search";

            String auth = sonarToken + ":";
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            Map body = response.getBody();

            if (body == null) return 0;

            Map paging = (Map) body.get("paging");

            return (int) paging.get("total");

        } catch (Exception e) {

            System.out.println("Failed to fetch Sonar project count: " + e.getMessage());
            return 0;
        }
    }
}