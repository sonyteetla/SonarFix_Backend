package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.dto.SystemHealth;
import com.company.codequality.sonarautofix.dto.SystemInfo;
import com.company.codequality.sonarautofix.service.SystemService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@CrossOrigin("*")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/health")
    public SystemHealth getHealth() {
        return systemService.getHealth();
    }

    @GetMapping("/info")
    public SystemInfo getInfo() {
        return systemService.getInfo();
    }
}
