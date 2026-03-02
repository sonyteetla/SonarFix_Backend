package com.company.codequality.sonarautofix.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class SonarProfileSetupService {

    @Value("${sonar.host.url}")
    private String sonarUrl;

    @Value("${sonar.token}")
    private String token;

    @Value("${sonar.profile.language}")
    private String language;

    @Value("${sonar.profile.name}")
    private String profileName;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SonarProfileSetupService(RestTemplate restTemplate,
                                    ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private boolean profileExists() throws Exception {

        String url = sonarUrl + "/api/qualityprofiles/search?language=" + language;

        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.GET,
                        new HttpEntity<>(authHeaders()),
                        String.class);

        JsonNode root = objectMapper.readTree(response.getBody());

        for (JsonNode profile : root.path("profiles")) {
            if (profileName.equals(profile.path("name").asText())) {
                return true;
            }
        }

        return false;
    }

    private void createProfile() {

        String url = sonarUrl + "/api/qualityprofiles/create";

        String body = "language=" + language + "&name=" + profileName;

        restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, formHeaders()),
                String.class
        );
    }

    private String getProfileKey() throws Exception {

        String url = sonarUrl + "/api/qualityprofiles/search?language=" + language;

        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.GET,
                        new HttpEntity<>(authHeaders()),
                        String.class);

        JsonNode root = objectMapper.readTree(response.getBody());

        for (JsonNode profile : root.path("profiles")) {
            if (profileName.equals(profile.path("name").asText())) {
                return profile.path("key").asText();
            }
        }

        throw new RuntimeException("Profile key not found");
    }

    private void setAsDefault() {

        String url = sonarUrl + "/api/qualityprofiles/set_default";

        String body = "language=" + language +
                      "&qualityProfile=" + profileName;

        restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, formHeaders()),
                String.class
        );
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(token, "");
        return headers;
    }

    private HttpHeaders formHeaders() {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }
    
    private void activateRules(String profileKey) {

    	List<String> rules = List.of(
    	        "java:S3749",
    	        "java:S3626",
    	        "java:S106",
    	        "java:S1128",
    	        "java:S108",
    	        "java:S1643",
    	        "java:S109",
    	        "java:S1874",
    	        "java:S6833",
    	        "java:S1075",
    	        "java:S2129",
    	        "java:S112",
    	        "java:S1118",
    	        "java:S1192",
    	        "java:S2095",
    	        "java:S3655",
    	        "java:S1656",
    	        "java:S1481",
    	        "java:S1854",
    	        "java:S1905",
    	        "java:S1698",
    	        "java:S1132",
    	        "java:S1604",
    	        "java:S1612",
    	        "java:S1319"
    	);

        for (String rule : rules) {

            String url = sonarUrl + "/api/qualityprofiles/activate_rule";

            String body = "key=" + profileKey + "&rule=" + rule;

            restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, formHeaders()),
                    String.class
            );
        }
    }
    
    public void setupIfNotExists() {

        try {

            String profileKey;

            if (!profileExists()) {
                createProfile();
            }

            profileKey = getProfileKey();

            activateRules(profileKey);   // ALWAYS ensure rules
            setAsDefault();

            System.out.println("Profile ensured with 14 rules.");

        } catch (Exception e) {
            throw new RuntimeException("Failed to setup Sonar profile", e);
        }
    }
}