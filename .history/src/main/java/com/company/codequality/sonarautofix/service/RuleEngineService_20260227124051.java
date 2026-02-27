package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.config.RuleRegistry;
import com.company.codequality.sonarautofix.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
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

// ================= ENRICH ISSUES =================  

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
            issue.setNonCompliantExample(data.getNonCompliantExample());  
            issue.setCompliantExample(data.getCompliantExample());  
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

// ================= FETCH RULE DATA =================  

private void fetchMissingRuleData(Set<String> ruleKeys) {  

    HttpHeaders headers = new HttpHeaders();  
    headers.setBasicAuth(token, "");  
    HttpEntity<Void> entity = new HttpEntity<>(headers);  

    for (String ruleKey : ruleKeys) {  

        if (ruleCache.containsKey(ruleKey)) continue;  

        try {  

            String url = sonarUrl + "/api/rules/show?key=" + ruleKey;  

            ResponseEntity<String> response =  
                    restTemplate.exchange(url,  
                            HttpMethod.GET,  
                            entity,  
                            String.class);  

            JsonNode root = objectMapper.readTree(response.getBody());  
            JsonNode ruleNode = root.path("rule");  

            String whyHtml = "";  
            String fixHtml = "";  

            if (ruleNode.has("descriptionSections")) {  

                for (JsonNode section :  
                        ruleNode.path("descriptionSections")) {  

                    String key =  
                            section.path("key").asText().toLowerCase();  

                    String content =  
                            section.path("content").asText();  

                    if ("root_cause".equals(key)) {  
                        whyHtml = content;  
                    }  

                    if ("how_to_fix".equals(key)) {  
                        fixHtml = content;  
                    }  
                }  
            }  

            List<ContentBlock> whyBlocks =  
                    parseHtmlToBlocks(whyHtml);  

            List<ContentBlock> fixBlocks =  
                    parseHtmlToBlocks(fixHtml);  

            Map<String, String> examples =  
                    extractCodeExamplesFromAllSections(ruleNode);  

            ruleCache.put(ruleKey,  
                    new RuleDescriptionData(  
                            whyBlocks,  
                            fixBlocks,  
                            examples.get("non"),  
                            examples.get("compliant")  
                    )  
            );  

        } catch (Exception e) {  

            ruleCache.put(ruleKey,  
                    new RuleDescriptionData(  
                            Collections.singletonList(  
                                    new ContentBlock(  
                                            "paragraph",  
                                            "Description unavailable"  
                                    )  
                            ),  
                            new ArrayList<>(),  
                            "",  
                            ""  
                    )  
            );  
        }  
    }  
}  

// ================= HTML → BLOCKS =================  

private List<ContentBlock> parseHtmlToBlocks(String html) {  

    List<ContentBlock> blocks = new ArrayList<>();  

    if (html == null || html.isBlank()) return blocks;  

    org.jsoup.nodes.Document doc = Jsoup.parse(html);  

    for (Element el : doc.body().children()) {  

        switch (el.tagName()) {  

            case "h1":  
            case "h2":  
            case "h3":  
                blocks.add(new ContentBlock(  
                        "heading",  
                        el.text()  
                ));  
                break;  

            case "p":  
                blocks.add(new ContentBlock(  
                        "paragraph",  
                        el.text()  
                ));  
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

            default:  
                break;  
        }  
    }  

    return blocks;  
}  

// ================= EXTRACT CODE EXAMPLES =================  

private Map<String, String> extractCodeExamplesFromAllSections(JsonNode ruleNode) {  

    Map<String, String> result = new HashMap<>();  
    result.put("non", "");  
    result.put("compliant", "");  

    StringBuilder non = new StringBuilder();  
    StringBuilder compliant = new StringBuilder();  

    if (!ruleNode.has("descriptionSections")) {  
        return result;  
    }  

    for (JsonNode section : ruleNode.get("descriptionSections")) {  

        String html = section.path("content").asText();  
        if (html == null || html.isBlank()) continue;  

        org.jsoup.nodes.Document doc = Jsoup.parse(html);  

        String currentMode = null; // "non" or "compliant"  

        for (org.jsoup.nodes.Element el : doc.body().children()) {  

            String tag = el.tagName();  
            String text = el.text().toLowerCase();  

            // Detect section switch 
            if (tag.matches("h1|h2|h3|h4|strong")) {  

                if (text.contains("noncompliant")) {  
                    currentMode = "non";  
                    continue;  
                }  

                if (text.contains("compliant")) {  
                    currentMode = "compliant";  
                    continue;  
                }  
            }  

            // Capture code blocks  
            if (tag.equals("pre") && currentMode != null) {  

                if (currentMode.equals("non")) {  
                    non.append(el.text()).append("\n\n");  
                }  

                if (currentMode.equals("compliant")) {  
                    compliant.append(el.text()).append("\n\n");  
                }  
            }  
        }  
    }  

    result.put("non", non.toString().trim());  
    result.put("compliant", compliant.toString().trim());  

    return result;  
}

}

