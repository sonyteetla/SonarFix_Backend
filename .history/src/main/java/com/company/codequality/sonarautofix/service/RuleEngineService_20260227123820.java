package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.config.RuleRegistry;
import com.company.codequality.sonarautofix.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RuleEngineService {

    @Value("${sonar.host.url}")
    private String sonarUrl;

    @Value("${sonar.token}")
    private String token;

    private final RuleRegistry ruleRegistry;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Cache Sonar rule descriptions
    private final Map<String, String> ruleDescriptionCache = new ConcurrentHashMap<>();

    public RuleEngineService(RuleRegistry ruleRegistry,
                             RestTemplate restTemplate,
                             ObjectMapper objectMapper) {
        this.ruleRegistry = ruleRegistry;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public void enrichIssues(List<Issue> issues) {

        if (issues == null || issues.isEmpty()) {
            return;
        }

        // Collect unique rule keys
        Set<String> ruleKeys = issues.stream()
                .map(Issue::getRule)
                .collect(Collectors.toSet());

        fetchMissingDescriptions(ruleKeys);

        for (Issue issue : issues) {

            // 1️⃣ Set Sonar description
            issue.setDescription(ruleDescriptionCache.get(issue.getRule()));

            // 2️⃣ Apply internal RuleRegistry logic
            RuleConfig rule = ruleRegistry.getRule(issue.getRule());

            if (rule != null) {

                issue.setSupported(true);
                issue.setAutoFixable(rule.isAutoFixable());

                try {
                    issue.setFixType(FixType.valueOf(rule.getFixType()));
                } catch (Exception e) {
                    issue.setFixType(FixType.NONE);
                }

            } else {

                issue.setSupported(false);
                issue.setAutoFixable(false);
                issue.setFixType(FixType.NONE);
            }
        }
    }

    private void fetchMissingDescriptions(Set<String> ruleKeys) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(token, "");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        for (String ruleKey : ruleKeys) {

            if (!ruleDescriptionCache.containsKey(ruleKey)) {

                try {

                    String url = sonarUrl + "/api/rules/show?key=" + ruleKey;

                    ResponseEntity<String> response =
                            restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode ruleNode = root.path("rule");

                    String description = "";

                    // Modern Sonar
                    if (ruleNode.has("descriptionSections")) {

                        for (JsonNode section : ruleNode.path("descriptionSections")) {

                            if ("root_cause"
                                    .equalsIgnoreCase(section.path("key").asText())) {

                                String rawHtml =
                                        section.path("content").asText();

                                // Strip HTML safely
                                description = Jsoup.parse(rawHtml).text();
                                break;
                            }
                        }
                    }

                    // Fallback for older Sonar
                    if (description.isEmpty() && ruleNode.has("htmlDesc")) {
                        description = Jsoup.parse(
                                ruleNode.path("htmlDesc").asText()
                        ).text();
                    }

                    if (description.isEmpty()) {
                        description = "No description available.";
                    }

                    ruleDescriptionCache.put(ruleKey, description);

                } catch (Exception e) {
                    ruleDescriptionCache.put(ruleKey,
                            "Description unavailable.");
                }
            }
        }
    }
}