package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.model.SonarIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import java.util.List;

@Service
public class SonarService {

    @Value("${sonar.token}")
    private String token;

    @Value("${sonar.host.url}")
    private String sonarHost;


    // ================= RUN SONAR SCAN =================
    public String runSonarScan(String projectPath, String projectKey, ScanTask task) {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ================= RUN SONAR SCAN =================
    public void runSonarScan(String projectPath,
                             String projectKey,
                             ScanTask task) {

        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "C:\\Program Files\\Apache\\Maven\\bin\\mvn.cmd",
                    "clean",
                    "verify",
                    "-DskipTests=true",
                    "org.sonarsource.scanner.maven:sonar-maven-plugin:sonar",
                    "-Dsonar.projectKey=" + projectKey,
                    "-Dsonar.host.url=" + sonarHost,
                    "-Dsonar.token=" + token
            );

            builder.directory(new java.io.File(projectPath));
            builder.redirectErrorStream(true);

            Process process = builder.start();

            StringBuilder logBuffer = new StringBuilder();

            String ceTaskId = null;


            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(
                                 process.getInputStream(),
                                 StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {

                    System.out.println(line);
                    logBuffer.append(line).append("\n");

                    // Extract CE task ID
                    if (line.contains("ce/task?id=")) {
                        int idx = line.indexOf("ce/task?id=");
                        ceTaskId = line.substring(idx + 11).trim();
                    }
                }
            }

            task.setBuildLog(logBuffer.toString());


            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.out.println("⚠ Maven build failed but continuing scan");

            process.waitFor();

            if (ceTaskId != null) {
                waitForCeTaskCompletion(ceTaskId);
            } else {
                throw new RuntimeException("CE Task ID not found in Sonar logs");

            }

        } catch (Exception e) {
            task.setBuildLog("Scan failed: " + e.getMessage());
            throw new RuntimeException("Sonar scan failed", e);
        }
    }


    // ================= FETCH ISSUES FROM SONAR =================
    public List<SonarIssue> fetchIssues(String projectKey) {
        try {
            String url = sonarHost + "/api/issues/search?projectKeys=" + projectKey + "&ps=500";

    // ================= WAIT FOR SONAR BACKGROUND PROCESS =================
    private void waitForCeTaskCompletion(String ceTaskId) throws Exception {

        System.out.println("Waiting for Sonar CE task: " + ceTaskId);

        String url = sonarHost + "/api/ce/task?id=" + ceTaskId;


        while (true) {

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(token, "");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("task").path("status").asText();

            if ("SUCCESS".equalsIgnoreCase(status)) {
                System.out.println("Sonar analysis completed successfully.");
                break;
            }

            if ("FAILED".equalsIgnoreCase(status)
                    || "CANCELED".equalsIgnoreCase(status)) {
                throw new RuntimeException("Sonar CE task failed: " + status);
            }

            Thread.sleep(2000);
        }
    }

    // ================= FETCH ISSUES (STRICT PROJECTKEY ONLY) =================
    public List<SonarIssue> fetchIssues(String projectKey) {

        try {

            String url = sonarHost +
                    "/api/issues/search?componentKeys=" +
                    projectKey +
                    "&resolved=false&ps=500";

            System.out.println("Fetching issues for projectKey = " + projectKey);
            System.out.println("URL = " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(token, "");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode issuesNode = root.path("issues");

            List<SonarIssue> issues = new ArrayList<>();

            if (root.has("issues")) {
                for (JsonNode node : root.get("issues")) {
                    SonarIssue issue = new SonarIssue();
                    issue.setRule(node.get("rule").asText());
                    issue.setComponent(node.get("component").asText());
                    issue.setLine(node.has("line") ? node.get("line").asInt() : 0);

            if (issuesNode.isArray()) {
                for (JsonNode node : issuesNode) {

                    SonarIssue issue = new SonarIssue();
                    issue.setRule(node.path("rule").asText());

                    String fullComponent = node.path("component").asText();
                    String filePath = fullComponent.contains(":")
                            ? fullComponent.substring(fullComponent.indexOf(":") + 1)
                            : fullComponent;

                    issue.setComponent(filePath);
                    issue.setLine(node.has("line") ? node.get("line").asInt() : 0);


                    issues.add(issue);
                }
            }

            System.out.println("Total issues fetched: " + issues.size());

            return issues;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}