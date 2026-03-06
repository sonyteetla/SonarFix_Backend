package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.config.RuleRegistry;
import com.company.codequality.sonarautofix.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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

    private final Map<String, RuleDescriptionData> ruleCache =
            new ConcurrentHashMap<>();

    public RuleEngineService(RuleRegistry ruleRegistry,
                             RestTemplate restTemplate,
                             ObjectMapper objectMapper) {
        this.ruleRegistry = ruleRegistry;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public void enrichIssues(List<Issue> issues) {

        if (issues == null || issues.isEmpty()) return;

        Set<String> ruleKeys = issues.stream()
                .map(Issue::getRule)
                .collect(Collectors.toSet());

        fetchMissingRuleData(ruleKeys);

        for (Issue issue : issues) {

            RuleDescriptionData data = ruleCache.get(issue.getRule());

            if (data != null) {
                issue.setWhyBlocks(data.getWhyBlocks());
            }

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

    private void fetchMissingRuleData(Set<String> ruleKeys) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(token, "");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        for (String ruleKey : ruleKeys) {

            if (ruleCache.containsKey(ruleKey)) continue;

            try {

                String url = sonarUrl + "/api/rules/show?key=" + ruleKey;

                ResponseEntity<String> response =
                        restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                String body = response.getBody();

                // ✅ SAFE JSON CHECK
                if (body == null || body.trim().startsWith("<")) {

                    System.out.println("⚠ Sonar returned HTML instead of JSON for rule: " + ruleKey);

                    ruleCache.put(
                            ruleKey,
                            new RuleDescriptionData(
                                    Collections.singletonList(
                                            new ContentBlock("paragraph",
                                                    "Description unavailable")
                                    )
                            )
                    );

                    continue;
                }

                JsonNode root = objectMapper.readTree(body);
                JsonNode ruleNode = root.path("rule");

                List<ContentBlock> whyBlocks = new ArrayList<>();

                if (ruleNode.has("descriptionSections")) {

                    for (JsonNode section :
                            ruleNode.get("descriptionSections")) {

                        String html = section.path("content").asText();

                        if (html != null && !html.isBlank()) {
                            whyBlocks.addAll(parseHtmlToBlocks(html));
                        }
                    }
                }

                ruleCache.put(ruleKey,
                        new RuleDescriptionData(whyBlocks));

            } catch (Exception e) {

                ruleCache.put(
                        ruleKey,
                        new RuleDescriptionData(
                                Collections.singletonList(
                                        new ContentBlock(
                                                "paragraph",
                                                "Description unavailable"
                                        )
                                )
                        )
                );
            }
        }
    }

    private List<ContentBlock> parseHtmlToBlocks(String html) {

        List<ContentBlock> blocks = new ArrayList<>();

        if (html == null || html.isBlank()) return blocks;

        Document doc = Jsoup.parse(html);

        for (Element el : doc.body().children()) {

            switch (el.tagName()) {

                case "h1":
                case "h2":
                case "h3":
                    blocks.add(new ContentBlock("heading", el.text()));
                    break;

                case "p":
                    blocks.add(new ContentBlock("paragraph", el.text()));
                    break;

                case "ul":
                    blocks.add(new ContentBlock(
                            "unordered_list",
                            el.select("li")
                                    .stream()
                                    .map(Element::text)
                                    .collect(Collectors.toList())
                    ));
                    break;

                case "ol":
                    blocks.add(new ContentBlock(
                            "ordered_list",
                            el.select("li")
                                    .stream()
                                    .map(Element::text)
                                    .collect(Collectors.toList())
                    ));
                    break;

                case "pre":

                    String diffType = el.attr("data-diff-type");

                    if ("noncompliant".equals(diffType)) {

                        blocks.add(new ContentBlock(
                                "noncompliant_code",
                                el.text()
                        ));

                    } else if ("compliant".equals(diffType)) {

                        blocks.add(new ContentBlock(
                                "compliant_code",
                                el.text()
                        ));

                    } else {

                        blocks.add(new ContentBlock(
                                "code",
                                el.text()
                        ));
                    }

                    break;

                default:
                    break;
            }
        }

        return blocks;
    }
}