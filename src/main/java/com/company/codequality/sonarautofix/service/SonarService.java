package com.company.codequality.sonarautofix.service;

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
     */
    public String runSonarScan(String projectPath, String projectKey) {

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

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            StringBuilder output = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException(
                        "Sonar scan failed. Exit code: " + exitCode + "\n" + output
                );
            }

            return output.toString();

        } catch (Exception e) {
            throw new RuntimeException("Sonar Scan Failed: " + e.getMessage(), e);
        }
    }

}
