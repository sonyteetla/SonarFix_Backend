package com.company.codequality.sonarautofix.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SonarIssueService {

    @Value("${sonar.host.url}")
    private String sonarUrl;

    @Value("${sonar.projectKey}")
    private String projectKey;

    public String fetchIssues() {

        String apiUrl = sonarUrl +
                "/api/issues/search?projectKeys=" + projectKey;

        RestTemplate restTemplate = new RestTemplate();

        return restTemplate.getForObject(apiUrl, String.class);
    }
}

