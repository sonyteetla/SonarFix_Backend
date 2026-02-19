package com.company.codequality.sonarautofix.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class SonarService {

    @Value("${sonar.projectKey}")
    private String projectKey;

    @Value("${sonar.projectBaseDir}")
    private String baseDir;
    
    @Value("${sonar.token}")
    private String token;


    public String runSonarScan() {
        try {

        	ProcessBuilder builder = new ProcessBuilder(
        	        "C:\\Program Files\\apache-maven-3.9.12-bin\\apache-maven-3.9.12\\bin\\mvn.cmd",
        	        "sonar:sonar",
        	        "-Dsonar.projectKey=" + projectKey,
        	        "-Dsonar.host.url=http://localhost:9000",
        	        "-Dsonar.token=" + token
        	);



            builder.directory(new java.io.File(baseDir));
            builder.redirectErrorStream(true);

            Process process = builder.start();

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            StringBuilder output = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();

            return output.toString();

        } catch (Exception e) {
            return "Sonar Scan Failed: " + e.getMessage();
        }
    }
}
