package com.company.codequality.sonarautofix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;   

@SpringBootApplication
@EnableAsync   
public class SonarAutofixApplication {

    public static void main(String[] args) {
        SpringApplication.run(SonarAutofixApplication.class, args);
    }
}
