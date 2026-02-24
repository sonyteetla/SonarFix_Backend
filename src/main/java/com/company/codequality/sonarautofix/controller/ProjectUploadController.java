package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.service.ProjectUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.Map;

@RestController
@RequestMapping("/api/project")
@CrossOrigin("*")
public class ProjectUploadController {

    private final ProjectUploadService uploadService;

    public ProjectUploadController(ProjectUploadService uploadService) {
        this.uploadService = uploadService;
    }

    // ZIP Upload
    @PostMapping(value = "/upload-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadZip(
            @RequestParam("file") MultipartFile file) {

        String projectDir = uploadService.handleZipUpload(file);

        return ResponseEntity.ok(
                Map.of(
                        "workspacePath", projectDir,
                        "status", "UPLOADED"
                )
        );
    }

    // GitHub Clone
    @PostMapping("/upload-github")
    public ResponseEntity<Map<String, String>> uploadGithub(
            @RequestParam("repoUrl") String repoUrl) {

        String projectDir = uploadService.cloneGithub(repoUrl);

        return ResponseEntity.ok(
                Map.of(
                        "workspacePath", projectDir,
                        "status", "CLONED"
                )
        );
    }

    // Local Directory
    @PostMapping("/upload-local")
    public ResponseEntity<Map<String, String>> uploadLocal(
            @RequestParam("localPath") String localPath) {

        String projectDir = uploadService.useLocalDirectory(localPath);

        return ResponseEntity.ok(
                Map.of(
                        "workspacePath", projectDir,
                        "status", "LOADED"
                )
        );
    }
}