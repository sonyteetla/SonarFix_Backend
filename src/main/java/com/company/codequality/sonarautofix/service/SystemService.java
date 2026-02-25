package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.dto.SystemHealth;
import com.company.codequality.sonarautofix.dto.SystemInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SystemService {

    @Value("${sonar.host.url}")
    private String sonarUrl;

    private final RestTemplate restTemplate;

    public SystemService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public SystemHealth getHealth() {
        boolean sonarUp = false;
        try {
            restTemplate.getForEntity(sonarUrl + "/api/system/status", String.class);
            sonarUp = true;
        } catch (Exception e) {
            // Sonar is down or unreachable
        }

        return SystemHealth.builder()
                .status("UP")
                .sonarConnectivity(sonarUp)
                .build();
    }

    public SystemInfo getInfo() {
        return SystemInfo.builder()
                .version("0.0.1-SNAPSHOT")
                .os(System.getProperty("os.name"))
                .javaVersion(System.getProperty("java.version"))
                .build();
    }
}
