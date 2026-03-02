package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.Project;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SonarProjectsFetchService {

    @Value("${sonar.host.url}")
    private String sonarUrl;

    @Value("${sonar.token}")
    private String sonarToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Project> fetchAllProjects() {

        List<Project> allProjects = new ArrayList<>();

        int page = 1;
        int pageSize = 100;
        boolean hasMore = true;

        while (hasMore) {

            String url = String.format(
                    "%s/api/projects/search?p=%d&ps=%d",
                    sonarUrl, page, pageSize
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(sonarToken, "");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = response.getBody();

            if (body == null || !body.containsKey("components")) {
                break;
            }

            List<Map<String, Object>> components =
                    (List<Map<String, Object>>) body.get("components");

            if (components.isEmpty()) {
                break;
            }

            List<Project> projects = components.stream().map(component -> {
                Project project = new Project();
                project.setId((String) component.get("key"));     // Use Sonar key
                project.setName((String) component.get("name"));
                return project;
            }).collect(Collectors.toList());

            allProjects.addAll(projects);

            Map<String, Object> paging =
                    (Map<String, Object>) body.get("paging");

            int total = (int) paging.get("total");
            hasMore = page * pageSize < total;
            page++;
        }

        return allProjects;
    }
}