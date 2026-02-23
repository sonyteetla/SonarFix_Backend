package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.service.ProjectUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

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
    public ResponseEntity<String> uploadZip(
            @RequestParam("file") MultipartFile file) {

        String path = uploadService.handleZipUpload(file);
        return ResponseEntity.ok("Project uploaded at: " + path);
    }

    // GitHub Clone
    @PostMapping("/upload-github")
    public ResponseEntity<String> uploadGithub(
            @RequestParam("repoUrl") String repoUrl) {

        String path = uploadService.cloneGithub(repoUrl);
        return ResponseEntity.ok("Project cloned at: " + path);
    }

    // Local Directory
    @PostMapping("/upload-local")
    public ResponseEntity<String> uploadLocal(
            @RequestParam("localPath") String localPath) {

        String path = uploadService.useLocalDirectory(localPath);
        return ResponseEntity.ok("Project loaded from: " + path);
    }
}
