package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

        String pagedUrl = buildSonarUrl(
                projectKey,
                softwareQualities,
                severities,
                rules,
                page,
                safePageSize
        );

        ResponseEntity<String> pagedResponse = callSonar(pagedUrl);

        IssueResponse pagedResult =
                processPagedResponse(pagedResponse.getBody(),
                        autoFixOnly,
                        page,
                        safePageSize);

        // --- FILTER COUNTS (kept exactly as your logic) ---

        List<Issue> severityBase = fetchAllIssuesForCounts(
                projectKey,
                softwareQualities,
                Collections.emptyList(),
                rules,
                autoFixOnly
        );

        List<Issue> qualityBase = fetchAllIssuesForCounts(
                projectKey,
                Collections.emptyList(),
                severities,
                rules,
                autoFixOnly
        );

        List<Issue> ruleBase = fetchAllIssuesForCounts(
                projectKey,
                softwareQualities,
                severities,
                Collections.emptyList(),
                autoFixOnly
        );

        Map<String, Long> severityCounts = severityBase.stream()
                .collect(Collectors.groupingBy(
                        Issue::getSeverity,
                        Collectors.counting()));

        Map<String, Long> qualityCounts = qualityBase.stream()
                .collect(Collectors.groupingBy(
                        Issue::getSoftwareQuality,
                        Collectors.counting()));

        Map<String, Long> ruleCounts = ruleBase.stream()
                .collect(Collectors.groupingBy(
                        Issue::getRule,
                        Collectors.counting()));

        FilterCounts filterCounts =
                new FilterCounts(severityCounts, qualityCounts, ruleCounts);

        return IssueResponse.builder()
                .totalElements(pagedResult.getTotalElements())
                .page(pagedResult.getPage())
                .pageSize(pagedResult.getPageSize())
                .totalPages(pagedResult.getTotalPages())
                .content(pagedResult.getContent())
                .filterCounts(filterCounts)
                .build();
    }

    // ================= PAGED RESPONSE =================

    private IssueResponse processPagedResponse(String body,
                                               Boolean autoFixOnly,
                                               int page,
                                               int pageSize) {

        try {

            JsonNode root = objectMapper.readTree(body);
            JsonNode issuesNode = root.path("issues");

            long total = root.path("paging").path("total").asLong();
            int sonarPageSize = root.path("paging").path("pageSize").asInt();

            List<Issue> parsedIssues =
                    parseIssuesFromSonar(issuesNode, autoFixOnly);

            Map<String, List<Issue>> grouped =
                    parsedIssues.stream()
                            .collect(Collectors.groupingBy(Issue::getFilePath));

            List<FileIssueGroup> content =
                    grouped.entrySet().stream()
                            .map(e -> new FileIssueGroup(e.getKey(), e.getValue()))
                            .collect(Collectors.toList());

            int totalPages =
                    (int) Math.ceil((double) total / sonarPageSize);

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

    // ================= FETCH ALL FOR COUNTS =================

    private List<Issue> fetchAllIssuesForCounts(String projectKey,
                                                List<String> softwareQualities,
                                                List<String> severities,
                                                List<String> rules,
                                                Boolean autoFixOnly) {

        int page = 1;
        int pageSize = 500;
        boolean hasMore = true;

        List<Issue> allIssues = new ArrayList<>();

        while (hasMore) {

            String url = buildSonarUrl(
                    projectKey,
                    softwareQualities,
                    severities,
                    rules,
                    page,
                    pageSize
            );

            ResponseEntity<String> response = callSonar(url);

            JsonNode root;
            try {
                root = objectMapper.readTree(response.getBody());
            } catch (Exception e) {
                throw new RuntimeException("Parsing error", e);
            }

            JsonNode issuesNode = root.path("issues");

            List<Issue> pageIssues =
                    parseIssuesFromSonar(issuesNode, autoFixOnly);

            allIssues.addAll(pageIssues);

            int total = root.path("paging").path("total").asInt();
            hasMore = page * pageSize < total;
            page++;
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
            throw new RuntimeException("SonarQube API returned error");
        }

        return response;
    }

    // ================= ISSUE PARSER =================

    private List<Issue> parseIssuesFromSonar(JsonNode issuesNode,
                                             Boolean autoFixOnly) {

        List<Issue> parsedIssues = new ArrayList<>();

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

                parsedIssues.add(issue);
            }
        }

        // 🔥 IMPORTANT:
        // Enrich AFTER parsing (adds description, fix info, examples, blocks, etc.)
        ruleEngineService.enrichIssues(parsedIssues);

        if (Boolean.TRUE.equals(autoFixOnly)) {
            parsedIssues = parsedIssues.stream()
                    .filter(Issue::isAutoFixable)
                    .collect(Collectors.toList());
        }

        return parsedIssues;
    }

    // ================= UTIL =================

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
            case "BUG": return "Reliability";
            case "VULNERABILITY": return "Security";
            case "CODE_SMELL": return "Maintainability";
            default: return "Unknown";
        }
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

            List<String> types = mapSoftwareQualityToTypes(softwareQualities);

            if (!types.isEmpty()) {
                url.append("&types=")
                        .append(String.join(",", types));
            }
        }

        if (rules != null && !rules.isEmpty()) {
            url.append("&rules=")
                    .append(String.join(",", rules));
        }

        return url.toString();
    }

    // ================= FETCH ALL (unchanged) =================

    public List<Issue> fetchAllIssues(String projectKey,
                                      List<String> softwareQualities,
                                      List<String> severities,
                                      List<String> rules) {

        int page = 1;
        int pageSize = 500;
        boolean hasMore = true;

        List<Issue> allIssues = new ArrayList<>();

        while (hasMore) {

            IssueResponse response =
                    fetchIssues(projectKey,
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

    // ================= DTO MAPPING =================

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
                issue.getFixType() != null ? issue.getFixType().name() : null
        );
    }
    public IssueResponse fetchAllIssuesForViewer(String projectKey,
            List<String> softwareQualities,
            List<String> severities,
            List<String> rules,
            Boolean autoFixOnly) {

int page = 1;
int pageSize = 500;
boolean hasMore = true;

List<Issue> allIssues = new ArrayList<>();

while (hasMore) {

String url = buildSonarUrl(
projectKey,
softwareQualities,
severities,
rules,
page,
pageSize
);

ResponseEntity<String> response = callSonar(url);

try {
JsonNode root = objectMapper.readTree(response.getBody());

JsonNode issuesNode = root.path("issues");

List<Issue> pageIssues =
parseIssuesFromSonar(issuesNode, autoFixOnly);

allIssues.addAll(pageIssues);

int total = root.path("paging").path("total").asInt();
hasMore = page * pageSize < total;
page++;

} catch (Exception e) {
throw new RuntimeException("Error fetching all issues", e);
}
}

// Group by file (same structure )
Map<String, List<Issue>> grouped =
allIssues.stream()
.collect(Collectors.groupingBy(Issue::getFilePath));

List<FileIssueGroup> content =
grouped.entrySet().stream()
.map(e -> new FileIssueGroup(e.getKey(), e.getValue()))
.collect(Collectors.toList());

return IssueResponse.builder()
.totalElements(allIssues.size())
.page(1)
.pageSize(allIssues.size())
.totalPages(1)
.content(content)
.filterCounts(null) 
.build();
}
}
