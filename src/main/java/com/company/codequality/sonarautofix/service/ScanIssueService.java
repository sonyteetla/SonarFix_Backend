package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScanIssueService {

    @Value("${sonar.host.url}")
    private String sonarUrl;

    @Value("${sonar.token}")
    private String token;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RuleEngineService ruleEngineService;

    public ScanIssueService(RestTemplate restTemplate,
                            ObjectMapper objectMapper,
                            RuleEngineService ruleEngineService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.ruleEngineService = ruleEngineService;
    }

    // ================= MAIN ENTRY =================

    public IssueResponse fetchIssues(String projectKey,
                                     List<String> softwareQualities,
                                     List<String> severities,
                                     List<String> rules,
                                     Boolean autoFixOnly,
                                     int page,
                                     int pageSize) {

        int safePageSize = Math.min(pageSize, 500);

        String url = buildSonarUrl(projectKey, softwareQualities, severities, rules, page, safePageSize);
        ResponseEntity<String> response = callSonar(url);

        IssueResponse paged = processResponse(response.getBody(), autoFixOnly, page, safePageSize);

        // Fetch all for counts
        List<Issue> allIssues = fetchAllIssues(projectKey, softwareQualities, severities, rules);
        FilterCounts counts = buildFilterCounts(allIssues);

        return IssueResponse.builder()
                .totalElements(paged.getTotalElements())
                .page(paged.getPage())
                .pageSize(paged.getPageSize())
                .totalPages(paged.getTotalPages())
                .content(paged.getContent())
                .filterCounts(counts)
                .build();
    }

    // ================= BUILD SONAR URL =================

    private String buildSonarUrl(String projectKey,
                                 List<String> softwareQualities,
                                 List<String> severities,
                                 List<String> rules,
                                 int page,
                                 int pageSize) {

        StringBuilder url = new StringBuilder();

        url.append(sonarUrl)
                .append("/api/issues/search?")
                .append("componentKeys=").append(projectKey)
                .append("&resolved=false")
                .append("&p=").append(page)
                .append("&ps=").append(pageSize);

        if (severities != null && !severities.isEmpty()) {
            url.append("&severities=").append(String.join(",", severities));
        }

        if (softwareQualities != null && !softwareQualities.isEmpty()) {
            List<String> types = mapSoftwareQualityToTypes(softwareQualities);
            if (!types.isEmpty()) {
                url.append("&types=").append(String.join(",", types));
            }
        }

        if (rules != null && !rules.isEmpty()) {
            url.append("&rules=").append(String.join(",", rules));
        }

        return url.toString();
    }

    // ================= PROCESS RESPONSE =================

    private IssueResponse processResponse(String body,
                                          Boolean autoFixOnly,
                                          int page,
                                          int pageSize) {

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode issuesNode = root.path("issues");

            long total = root.path("paging").path("total").asLong();
            int sonarPageSize = root.path("paging").path("pageSize").asInt();

            List<Issue> issues = parseIssuesFromSonar(issuesNode);

            // Apply AutoFix filter
            if (Boolean.TRUE.equals(autoFixOnly)) {
                issues = issues.stream()
                        .filter(Issue::isAutoFixable)
                        .collect(Collectors.toList());
                total = issues.size();
            }

            // Group by file
            Map<String, List<Issue>> grouped = issues.stream()
                    .collect(Collectors.groupingBy(Issue::getFilePath));

            List<FileIssueGroup> content = grouped.entrySet().stream()
                    .map(e -> new FileIssueGroup(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());

            int totalPages = (int) Math.ceil((double) total / sonarPageSize);

            return IssueResponse.builder()
                    .totalElements(total)
                    .page(page)
                    .pageSize(pageSize)
                    .totalPages(totalPages)
                    .content(content)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed parsing Sonar response", e);
        }
    }

    // ================= FETCH ALL ISSUES =================

    public List<Issue> fetchAllIssues(String projectKey,
                                      List<String> softwareQualities,
                                      List<String> severities,
                                      List<String> rules) {

        int page = 1;
        int pageSize = 500;
        boolean hasMore = true;

        List<Issue> allIssues = new ArrayList<>();

        while (hasMore) {
            String url = buildSonarUrl(projectKey, softwareQualities, severities, rules, page, pageSize);
            ResponseEntity<String> response = callSonar(url);

            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode issuesNode = root.path("issues");

                List<Issue> pageIssues = parseIssuesFromSonar(issuesNode);
                allIssues.addAll(pageIssues);

                int total = root.path("paging").path("total").asInt();
                hasMore = page * pageSize < total;
                page++;

            } catch (Exception e) {
                throw new RuntimeException("Error parsing Sonar response", e);
            }
        }

        return allIssues;
    }

    // ================= SONAR CALL =================

    private ResponseEntity<String> callSonar(String url) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(token, "");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("SonarQube API error: " + response.getStatusCode());
        }

        return response;
    }

    // ================= PARSE ISSUES =================

    private List<Issue> parseIssuesFromSonar(JsonNode issuesNode) {

        List<Issue> issues = new ArrayList<>();

        if (issuesNode.isArray()) {
            for (JsonNode node : issuesNode) {

                String type = node.path("type").asText();
                String fullComponent = node.path("component").asText();

                String filePath = fullComponent.contains(":")
                        ? fullComponent.substring(fullComponent.indexOf(":") + 1)
                        : fullComponent;

                Issue issue = Issue.builder()
                        .key(node.path("key").asText())
                        .rule(node.path("rule").asText())
                        .severity(node.path("severity").asText())
                        .type(type)
                        .softwareQuality(mapTypeToSoftwareQuality(type))
                        .message(node.path("message").asText())
                        .filePath(filePath)
                        .line(node.has("line") ? node.get("line").asInt() : null)
                        .build();

                issues.add(issue);
            }
        }

        // Enrich rules
        ruleEngineService.enrichIssues(issues);

        // Strip HTML
        issues.forEach(issue -> {
            if (issue.getDescription() != null) {
                issue.setDescription(stripHtml(issue.getDescription()));
            }
        });

        return issues;
    }

    // ================= FILTER COUNTS =================

    private FilterCounts buildFilterCounts(List<Issue> issues) {

        Map<String, Long> severityCounts =
                issues.stream().collect(Collectors.groupingBy(Issue::getSeverity, Collectors.counting()));

        Map<String, Long> qualityCounts =
                issues.stream().collect(Collectors.groupingBy(Issue::getSoftwareQuality, Collectors.counting()));

        Map<String, Long> ruleCounts =
                issues.stream().collect(Collectors.groupingBy(Issue::getRule, Collectors.counting()));

        return new FilterCounts(severityCounts, qualityCounts, ruleCounts);
    }

    // ================= UTIL =================

    private String stripHtml(String html) {
        return Jsoup.parse(html == null ? "" : html).text().trim();
    }

    private List<String> mapSoftwareQualityToTypes(List<String> qualities) {
        List<String> types = new ArrayList<>();
        for (String q : qualities) {
            switch (q.toUpperCase()) {
                case "SECURITY": types.add("VULNERABILITY"); break;
                case "RELIABILITY": types.add("BUG"); break;
                case "MAINTAINABILITY": types.add("CODE_SMELL"); break;
            }
        }
        return types;
    }

    private String mapTypeToSoftwareQuality(String type) {
        switch (type) {
            case "BUG": return "Reliability";
            case "VULNERABILITY": return "Security";
            case "CODE_SMELL": return "Maintainability";
            default: return "Unknown";
        }
    }

    // ================= MAPPING =================

    public List<MappedIssue> toMappedIssues(List<Issue> issues) {
        if (issues == null) return new ArrayList<>();
        return issues.stream().map(this::toMappedIssue).collect(Collectors.toList());
    }

    public MappedIssue toMappedIssue(Issue issue) {
        return new MappedIssue(
                issue.getRule(),
                issue.getFilePath(),
                issue.getLine() != null ? issue.getLine() : 0,
                issue.getMessage(),
                issue.getSeverity(),
                issue.getSoftwareQuality(),
                issue.isAutoFixable(),
                issue.getFixType() != null ? issue.getFixType().name() : null
        );
    }
}