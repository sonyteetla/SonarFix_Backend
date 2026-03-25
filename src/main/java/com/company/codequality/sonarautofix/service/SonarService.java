package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.model.SonarIssue;
import com.company.codequality.sonarautofix.repository.ScanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class SonarService {

    @Value("${sonar.token}")
    private String token;

    @Value("${sonar.host.url}")
    private String sonarHost;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ScanRepository scanRepository;

    public SonarService(ScanRepository scanRepository) {
        this.scanRepository = scanRepository;
    }
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void runSonarScan(String projectPath,
            String projectKey,
            ScanTask task) {

try {
	ProcessBuilder builder = new ProcessBuilder(
		    "C:\\Program Files\\Apache\\Maven\\bin\\mvn.cmd",
		    "compile",
		    "sonar:sonar",         
		    "-DskipTests=true",
		    "-Dsonar.projectKey=" + projectKey,
		    "-Dsonar.host.url=" + sonarHost,
		    "-Dsonar.login=" + token
		);

builder.directory(new java.io.File(projectPath));
builder.redirectErrorStream(true);

Process process = builder.start();

StringBuilder logBuffer = new StringBuilder();
task.setBuildLog("");
String ceTaskId = null;

task.setProgress(10);
scanRepository.update(task);

try (BufferedReader reader =
         new BufferedReader(new InputStreamReader(
                 process.getInputStream(),
                 StandardCharsets.UTF_8))) {

String line;
int lineCount = 0;
int lastProgress = task.getProgress();

while ((line = reader.readLine()) != null) {

    System.out.println(line);
    logBuffer.append(line).append("\n");

    // ✅ Extract CE Task ID
    if (line.contains("/api/ce/task?id=")) {
        int idx = line.indexOf("/api/ce/task?id=");
        ceTaskId = line.substring(idx + 16).trim();

        if (ceTaskId.contains(" ")) {
            ceTaskId = ceTaskId.substring(0, ceTaskId.indexOf(" "));
        }

        System.out.println("CE Task ID: " + ceTaskId);
    }

    lineCount++;

    if (lineCount % 40 == 0) {
        int newProgress = Math.min(60, lastProgress + 2);

        if (newProgress != lastProgress) {
            lastProgress = newProgress;

            task.setProgress(newProgress);
            task.setBuildLog(logBuffer.toString());
            scanRepository.update(task);
        }
    }
}
}

int exitCode = process.waitFor();

task.setProgress(70);
scanRepository.update(task);

if (exitCode != 0) {
task.setStatus("FAILED");
scanRepository.update(task);
throw new RuntimeException("Maven build failed. Scan aborted.");
}

if (ceTaskId != null) {
waitForCeTaskCompletion(ceTaskId, task);
} else {
System.out.println("WARNING: CE Task ID not found. Skipping wait.");
}

} catch (Exception e) {

task.setBuildLog(
    (task.getBuildLog() != null ? task.getBuildLog() : "") +
            "\nScan failed: " + e.getMessage()
);
scanRepository.update(task);

throw new RuntimeException("Sonar scan failed", e);
}
}
    // ================= WAIT FOR SONAR BACKGROUND PROCESS =================
    private void waitForCeTaskCompletion(String ceTaskId, ScanTask task) throws Exception {

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

                // ✅ Update progress after CE completes
                task.setProgress(85);
                scanRepository.update(task);

                break;
            }

            if ("FAILED".equalsIgnoreCase(status)
                    || "CANCELED".equalsIgnoreCase(status)) {

                throw new RuntimeException("Sonar CE task failed: " + status);
            }

            Thread.sleep(2000);
        }
    }
    // ================= FETCH ISSUES FROM SONAR =================
    public List<SonarIssue> fetchIssues(String projectKey) {

        List<SonarIssue> allIssues = new ArrayList<>();

        int page = 1;
        int pageSize = 500;
        int maxResults = 10000;
        boolean hasMore = true;

        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(token, "");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            while (hasMore) {

                String url = sonarHost +
                        "/api/issues/search?componentKeys=" + projectKey +
                        "&resolved=false" +
                        "&p=" + page +
                        "&ps=" + pageSize +
                        "&languages=java"; // optional but useful

                System.out.println("Fetching page " + page + " → " + url);

                ResponseEntity<String> response =
                        restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode issuesNode = root.path("issues");

                if (issuesNode.isArray()) {

                    for (JsonNode node : issuesNode) {

                        String fullComponent = node.path("component").asText();

                        String filePath = fullComponent.contains(":")
                                ? fullComponent.substring(fullComponent.indexOf(":") + 1)
                                : fullComponent;

                        // ✅ FILTER ONLY JAVA FILES (mandatory)
                        if (!filePath.endsWith(".java")) {
                            continue;
                        }

                        SonarIssue issue = new SonarIssue();

                        issue.setKey(node.path("key").asText());
                        issue.setRule(node.path("rule").asText());
                        issue.setComponent(filePath);

                        int line = node.has("line") ? node.get("line").asInt() : -1;
                        issue.setLine(line);

                        allIssues.add(issue);
                    }
                }

                int total = root.path("paging").path("total").asInt();

                // ✅ CRITICAL FIX (prevents 10k crash)
                hasMore = page * pageSize < Math.min(total, maxResults);

                page++;
            }

            System.out.println("Total issues fetched (filtered .java): " + allIssues.size());

            return allIssues;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}