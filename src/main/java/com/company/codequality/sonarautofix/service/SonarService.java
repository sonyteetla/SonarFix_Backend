package com.company.codequality.sonarautofix.service;


import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.model.SonarIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class SonarService {

    @Value("${sonar.token}")
    private String token;

    @Value("${sonar.host.url}")
    private String sonarHost;

    public String runSonarScan(String projectPath, String projectKey, ScanTask task) {

        try {

            ProcessBuilder builder = new ProcessBuilder(
                    "mvn.cmd",
                    "clean",
                    "verify",
                    "-DskipTests=true",
                    "-Dmaven.test.failure.ignore=true",
                    "-Dmaven.compiler.failOnError=false",
                    "org.sonarsource.scanner.maven:sonar-maven-plugin:sonar",
                    "-Dsonar.projectKey=" + projectKey,
                    "-Dsonar.host.url=" + sonarHost,
                    "-Dsonar.token=" + token);

            builder.directory(new java.io.File(projectPath));
            builder.redirectErrorStream(true);

            Process process = builder.start();


            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder logBuffer = new StringBuilder();


            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    logBuffer.append(line).append("\n");
                }
            }

            task.setBuildLog(logBuffer.toString());

            int exitCode = process.waitFor();

            if (exitCode != 0) {

                throw new RuntimeException(
                        "Sonar scan failed. Exit code: " + exitCode + "\n" + output);

                System.out.println("⚠ Maven build failed but continuing scan");

            }

            return logBuffer.toString();

        } catch (Exception e) {
            task.setBuildLog("Scan failed: " + e.getMessage());
            return "Scan completed with errors";
        }
    }


}

    public List<SonarIssue> fetchIssues(String projectKey) {
        try {
            String url = sonarHost + "/api/issues/search?projectKeys=" + projectKey + "&ps=500";

            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            String auth = token + ":";
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.add("Authorization", "Basic " + encodedAuth);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());

            List<SonarIssue> issues = new ArrayList<>();

            for (JsonNode node : root.get("issues")) {
                SonarIssue issue = new SonarIssue();
                issue.setRule(node.get("rule").asText());
                issue.setComponent(node.get("component").asText());
                issue.setLine(node.has("line") ? node.get("line").asInt() : 0);
                issues.add(issue);
            }

            return issues;

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}

