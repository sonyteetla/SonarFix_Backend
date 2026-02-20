package com.company.codequality.sonarautofix.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class SonarService {

    @Value("${sonar.token}")
    private String token;
    
    @Value("${sonar.projectKey}")
    private String projectKey;


    /**
     * Runs Maven + Sonar scan on given project path
     */
    public String runSonarScan(String projectPath) {

        try {

            // 🔹 Unique project key (prevents overwrite in SonarQube)
            String dynamicProjectKey = "auto-project-" + System.currentTimeMillis();

            ProcessBuilder builder = new ProcessBuilder(
                    "C:\\Program Files\\apache-maven-3.9.12-bin\\apache-maven-3.9.12\\bin\\mvn.cmd",
                    "clean",
                    "verify",
                    "sonar:sonar",
                    "-Dsonar.projectKey=" + dynamicProjectKey,
                    "-Dsonar.host.url=http://localhost:9000",
                    "-Dsonar.token=" + token
            );

            // 🔹 Set project directory where pom.xml exists
            builder.directory(new java.io.File(projectPath));

            // 🔹 Merge error stream into output
            builder.redirectErrorStream(true);

            Process process = builder.start();

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            StringBuilder output = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                System.out.println(line);          // show in console
                output.append(line).append("\n");  // store result
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException(
                        "Sonar scan failed. Exit code: " + exitCode + "\n" + output
                );
            }

            return "✅ Sonar Scan Completed Successfully\nProjectKey: "
                    + dynamicProjectKey + "\n\n" + output;

        } catch (Exception e) {
            throw new RuntimeException("❌ Sonar Scan Failed: " + e.getMessage(), e);
        }
    }
}
