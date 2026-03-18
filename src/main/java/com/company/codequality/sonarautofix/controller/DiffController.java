package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.FileDiff;
import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.repository.ScanRepository;
import com.company.codequality.sonarautofix.service.ProjectDiffService;
import com.company.codequality.sonarautofix.service.ScanService;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diff")
@CrossOrigin
public class DiffController {

    private final ProjectDiffService diffService;
    private final ScanRepository scanRepository;
    private final ScanService scanService;

    public DiffController(ProjectDiffService diffService,
                          ScanRepository scanRepository,
                          ScanService scanService) {
        this.diffService = diffService;
        this.scanRepository = scanRepository;
        this.scanService = scanService;
    }

    // ================= GET DIFF =================
    @GetMapping("/project/{scanId}")
    public List<FileDiff> compareProjects(@PathVariable String scanId) {

        ScanTask task = scanRepository.findById(scanId);

        if (task == null) {
            throw new RuntimeException("Scan task not found for id: " + scanId);
        }

        String original = task.getProjectPath();     // ✅ ORIGINAL
        String preview = task.getPreviewPath();      // 🔥 FIXED VERSION

        System.out.println("🟢 ORIGINAL PATH: " + original);
        System.out.println("🟢 FIXED PATH: " + preview);

        // ✅ SAFETY CHECK
        if (preview == null || preview.isBlank()) {
            throw new RuntimeException("Preview path not found. Please run preview first.");
        }

        return diffService.compareProjects(original, preview);
    }

    // ================= 🔥 PREVIEW FIX =================
    @PostMapping("/preview/{scanId}")
    public String previewFixes(
            @PathVariable String scanId,
            @RequestBody Map<String, Object> request
    ) {

        if (request == null) {
            throw new IllegalArgumentException("Request body is missing");
        }

        String projectPath = (String) request.get("projectPath");

        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalArgumentException("projectPath is required");
        }

        Object fixesObj = request.get("fixes");

        List<Map<String, Object>> fixes;

        if (fixesObj instanceof List) {
            fixes = (List<Map<String, Object>>) fixesObj;
        } else {
            fixes = List.of(); // safe fallback
        }

        System.out.println("🔥 CONTROLLER RECEIVED PATH: " + projectPath);
        System.out.println("🔥 FIXES COUNT: " + fixes.size());

        scanService.previewFixes(projectPath, scanId, fixes);

        return "Preview generated successfully";
    }
}