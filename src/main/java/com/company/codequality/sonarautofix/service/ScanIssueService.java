package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.Issue;
import com.company.codequality.sonarautofix.model.IssueResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class ScanIssueService {

    @Value("${sonar.host.url}")
    private String sonarUrl;

    @Value("${sonar.token}")
    private String token;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();


	public IssueResponse fetchIssues(String projectKey,
                                     List<String> softwareQualities,
                                     List<String> severities,
                                     List<String> statuses,
                                     List<String> tags,
                                     int page,
                                     int pageSize) {

        try {

            // Map Software Quality to Types
            List<String> types = mapSoftwareQualityToTypes(softwareQualities);

            StringBuilder apiUrl = new StringBuilder();
            apiUrl.append(sonarUrl)
                    .append("/api/issues/search?")
                    .append("componentKeys=").append(projectKey)
                    .append("&branch=main")
                    .append("&p=").append(page)
                    .append("&ps=").append(pageSize);

            if (types != null && !types.isEmpty()) {
                apiUrl.append("&types=").append(String.join(",", types));
            }

            if (severities != null && !severities.isEmpty()) {
                apiUrl.append("&severities=").append(String.join(",", severities));
            }

            if (statuses != null && !statuses.isEmpty()) {
                apiUrl.append("&statuses=").append(String.join(",", statuses));
            }

            if (tags != null && !tags.isEmpty()) {
                apiUrl.append("&tags=").append(String.join(",", tags));
            }

            // Authorization
            String auth = token + ":";
            String encodedAuth = Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(apiUrl.toString(), HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            int total = root.get("total").asInt();

            List<Issue> issueList = new ArrayList<>();

            for (JsonNode node : root.get("issues")) {

                Issue issue = new Issue();

                String type = node.get("type").asText();

                issue.setKey(node.get("key").asText());
                issue.setRule(node.get("rule").asText());
                issue.setSeverity(node.get("severity").asText());
                issue.setType(type);
                issue.setSoftwareQuality(mapTypeToSoftwareQuality(type));
                issue.setMessage(node.get("message").asText());
                issue.setFilePath(node.get("component").asText());

                if (node.has("line")) {
                    issue.setLine(node.get("line").asInt());
                }

                issueList.add(issue);
            }

            IssueResponse issueResponse = new IssueResponse();
            issueResponse.setTotal(total);
            issueResponse.setIssues(issueList);
            issueResponse.setPage(page);
            issueResponse.setPageSize(pageSize);

            return issueResponse;

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch issues from SonarQube", e);
        }
    }

    // Map UI Software Quality to Sonar Types
    private List<String> mapSoftwareQualityToTypes(List<String> qualities) {

        if (qualities == null || qualities.isEmpty()) {
            return null;
        }

        List<String> types = new ArrayList<>();

        for (String quality : qualities) {
            switch (quality.toUpperCase()) {
                case "SECURITY":
                    types.add("VULNERABILITY");
                    break;
                case "RELIABILITY":
                    types.add("BUG");
                    break;
                case "MAINTAINABILITY":
                    types.add("CODE_SMELL");
                    break;
            }
        }

        return types;
    }

    // Map Sonar Type to UI Software Quality
    private String mapTypeToSoftwareQuality(String type) {

        switch (type) {
            case "BUG":
                return "Reliability";
            case "VULNERABILITY":
                return "Security";
            case "CODE_SMELL":
                return "Maintainability";
            default:
                return "Unknown";
        }
    }
}