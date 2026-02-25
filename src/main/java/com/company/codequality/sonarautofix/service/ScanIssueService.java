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

    public IssueResponse fetchIssues(String projectKey,
            List<String> softwareQualities,
            List<String> severities,
            List<String> rules,
            Boolean autoFixOnly,
            int page,
            int pageSize) {

        // 🔥 Enforce Sonar limit
        int safePageSize = Math.min(pageSize, 500);

        String url = buildSonarUrl(
                projectKey,
                softwareQualities,
                severities,
                rules,
                page,
                safePageSize);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(token, "");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> sonarResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!sonarResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("SonarQube API returned error");
        }

        System.out.println("Sonar Response Status: " + sonarResponse.getStatusCode());
        System.out.println("Sonar Response Body Sample: " + (sonarResponse.getBody() != null
                ? sonarResponse.getBody().substring(0, Math.min(200, sonarResponse.getBody().length()))
                : "null"));

        return processResponse(
                sonarResponse.getBody(),
                autoFixOnly,
                page,
                safePageSize);
    }

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
            url.append("&severities=")
                    .append(String.join(",", severities));
        }

        if (softwareQualities != null && !softwareQualities.isEmpty()) {
            url.append("&types=")
                    .append(String.join(",", mapSoftwareQualityToTypes(softwareQualities)));
        }

        if (rules != null && !rules.isEmpty()) {
            url.append("&rules=")
                    .append(String.join(",", rules));
        }

        return url.toString();
    }

    private IssueResponse processResponse(String body,
            Boolean autoFixOnly,
            int page,
            int pageSize) {

        try {

            JsonNode root = objectMapper.readTree(body);
            JsonNode issuesNode = root.path("issues");

            JsonNode pagingNode = root.path("paging");
            long total = pagingNode.path("total").asLong();
            int sonarPageSize = pagingNode.path("pageSize").asInt();

            List<Issue> issues = new ArrayList<>();

            if (issuesNode.isArray()) {
                for (JsonNode node : issuesNode) {

                    String type = node.path("type").asText();

                    String fullComponent = node.path("component").asText();
                    String filePath = fullComponent.contains(":")
                            ? fullComponent.substring(fullComponent.indexOf(":") + 1)
                            : fullComponent;

                    JsonNode textRange = node.path("textRange");

                    Integer startLine = null;
                    Integer endLine = null;
                    Integer startOffset = null;
                    Integer endOffset = null;

                    if (!textRange.isMissingNode()) {
                        startLine = textRange.path("startLine").asInt();
                        endLine = textRange.path("endLine").asInt();
                        startOffset = textRange.path("startOffset").asInt();
                        endOffset = textRange.path("endOffset").asInt();
                    }

                    Issue issue = Issue.builder()
                            .key(node.path("key").asText())
                            .rule(node.path("rule").asText())
                            .severity(node.path("severity").asText())
                            .type(type)
                            .softwareQuality(mapTypeToSoftwareQuality(type))
                            .message(node.path("message").asText())
                            .filePath(filePath)
                            .line(node.has("line") ? node.get("line").asInt() : null)

                            .startLine(startLine)
                            .endLine(endLine)
                            .startOffset(startOffset)
                            .endOffset(endOffset)

                            .build();

                    issues.add(issue);
                }
            }

            // Enrich issues (description + autofix)
            ruleEngineService.enrichIssues(issues);

            // Strip HTML
            for (Issue issue : issues) {
                if (issue.getDescription() != null) {
                    issue.setDescription(stripHtml(issue.getDescription()));
                }
            }

            // Auto-fix filter
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

            // 🔥 Correct total pages calculation using Sonar paging
            int totalPages = (int) Math.ceil((double) total / sonarPageSize);

            return IssueResponse.builder()
                    .totalElements(total)
                    .page(page)
                    .pageSize(pageSize)
                    .totalPages(totalPages)
                    .content(content)
                    .filterCounts(buildFilterCounts(issues))
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Sonar response", e);
        }
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text().trim();
    }

    private FilterCounts buildFilterCounts(List<Issue> issues) {

        Map<String, Long> severityCounts = issues.stream()
                .collect(Collectors.groupingBy(
                        Issue::getSeverity,
                        Collectors.counting()));

        Map<String, Long> qualityCounts = issues.stream()
                .collect(Collectors.groupingBy(
                        Issue::getSoftwareQuality,
                        Collectors.counting()));

        Map<String, Long> ruleCounts = issues.stream()
                .collect(Collectors.groupingBy(
                        Issue::getRule,
                        Collectors.counting()));

        return new FilterCounts(severityCounts, qualityCounts, ruleCounts);
    }

    private List<String> mapSoftwareQualityToTypes(List<String> qualities) {

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

    // 🔥 Fetch ALL issues safely using pagination
    public List<Issue> fetchAllIssues(String projectKey,
            List<String> softwareQualities,
            List<String> severities,
            List<String> rules) {

        int page = 1;
        int pageSize = 500;
        boolean hasMore = true;

        List<Issue> allIssues = new ArrayList<>();

        while (hasMore) {

            IssueResponse response = fetchIssues(projectKey,
                    softwareQualities,
                    severities,
                    rules,
                    false,
                    page,
                    pageSize);

            response.getContent()
                    .forEach(group -> allIssues.addAll(group.getIssues()));

            hasMore = page < response.getTotalPages();
            page++;
        }

        return allIssues;
    }

    public List<MappedIssue> toMappedIssues(List<Issue> issues) {
        if (issues == null)
            return new ArrayList<>();
        return issues.stream()
                .map(this::toMappedIssue)
                .collect(Collectors.toList());
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
                issue.getFixType() != null ? issue.getFixType().name() : null);
    }
}