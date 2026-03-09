package com.company.codequality.sonarautofix;

import com.company.codequality.sonarautofix.service.SonarProfileSetupService;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SonarAutofixApplication {

    private final SonarProfileSetupService setupService;

    public SonarAutofixApplication(SonarProfileSetupService setupService) {
        this.setupService = setupService;
    }

    public static void main(String[] args) {
        SpringApplication.run(SonarAutofixApplication.class, args);
    }

    @PostConstruct
    public void init() {
        try {
            setupService.setupIfNotExists();
        } catch (Exception e) {
            System.out.println("Sonar setup failed: " + e.getMessage());
        }
    }
}