package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.service.ProjectUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.Map;

@RestController
@RequestMapping("/api/project")
public class ProjectUploadController {

    private final ProjectUploadService uploadService;

    public ProjectUploadController(ProjectUploadService uploadService) {
        this.uploadService = uploadService;
    }

    // ================= ZIP Upload =================
    @PostMapping(value = "/upload-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadZip(@RequestParam("file") MultipartFile file) {

        String path = uploadService.handleZipUpload(file);

        return ResponseEntity.ok(
                Map.of("projectPath", path)
        );
    }

    // ================= GitHub Clone =================
    @PostMapping("/upload-github")
    public ResponseEntity<?> uploadGithub(@RequestParam("repoUrl") String repoUrl) {

        String path = uploadService.cloneGithub(repoUrl);

        return ResponseEntity.ok(
                Map.of("projectPath", path)
        );
    }

    // ================= Local Directory =================
    @PostMapping("/upload-local")
    public ResponseEntity<?> uploadLocal(@RequestParam("localPath") String localPath) {

        String path = uploadService.useLocalDirectory(localPath);

        return ResponseEntity.ok(
                Map.of("projectPath", path)
        );
    }
}