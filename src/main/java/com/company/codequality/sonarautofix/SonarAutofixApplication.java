package com.company.codequality.sonarautofix;

import com.company.codequality.sonarautofix.service.SonarProfileSetupService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SonarAutofixApplication {

    private final SonarProfileSetupService setupService;

    public SonarAutofixApplication(SonarProfileSetupService setupService) {
        this.setupService = setupService;
    }

    public static void main(String[] args) {
        SpringApplication.run(SonarAutofixApplication.class, args);
    }

    /*
     * @PostConstruct
     * public void init() {
     * try {
     * setupService.setupIfNotExists();
     * } catch (Exception e) {
     * System.out.println("Sonar setup failed: " + e.getMessage());
     * }
     * }
     */
}