package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.ScanTask;
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

    /**
     * Runs Maven + Sonar scan
     * Captures full build log into ScanTask
     */
    public void runSonarScan(String projectPath,
            String projectKey,
            ScanTask task) {

StringBuilder output = new StringBuilder();

try {

ProcessBuilder builder = new ProcessBuilder(
        "C:\\Program Files\\Apache\\Maven\\bin\\mvn.cmd",
        "clean",
        "verify",
        "-DskipTests=true",
        "org.sonarsource.scanner.maven:sonar-maven-plugin:sonar",
        "-Dsonar.projectKey=" + projectKey,
        "-Dsonar.host.url=http://localhost:9000",
        "-Dsonar.token=" + token

);

builder.directory(new java.io.File(projectPath));
builder.redirectErrorStream(true);


Process process = builder.start();

try (BufferedReader reader =
        new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

String line;

while ((line = reader.readLine()) != null) {
   output.append(line).append("\n");
}
}

int exitCode = process.waitFor();

task.setBuildLog(output.toString());

if (exitCode != 0) {
throw new RuntimeException(
       "Sonar scan failed. Exit code: " + exitCode
);
}

} catch (Exception e) {

task.setBuildLog(output.toString());

throw new RuntimeException(
   "Sonar Scan Failed: " + e.getMessage(),
   e
);
}
}
}
