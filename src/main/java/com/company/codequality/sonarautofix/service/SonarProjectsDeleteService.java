package com.company.codequality.sonarautofix.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SonarProjectsDeleteService {

    @Value("${sonar.host.url}")
    private String sonarUrl;

    @Value("${sonar.token}")
    private String sonarToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public String deleteProject(String projectKey) {

        String url = sonarUrl + "/api/projects/delete?project=" + projectKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(sonarToken, "");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        return "Project deleted successfully: " + projectKey;
    }
}