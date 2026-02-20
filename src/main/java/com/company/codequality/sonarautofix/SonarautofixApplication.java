package com.company.codequality.sonarautofix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;   

@SpringBootApplication
@EnableAsync   
public class SonarautofixApplication {

    public static void main(String[] args) {
        SpringApplication.run(SonarautofixApplication.class, args);
    }
}
