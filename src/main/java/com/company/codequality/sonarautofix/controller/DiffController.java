package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.FileDiff;
import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.repository.ScanRepository;
import com.company.codequality.sonarautofix.service.ProjectDiffService;
import com.company.codequality.sonarautofix.service.ScanService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/diff")
@CrossOrigin
public class DiffController {

    private final ProjectDiffService diffService;
    private final ScanRepository scanRepository;
    private final ScanService scanService;
    
    public DiffController(ProjectDiffService diffService,ScanRepository scanRepository, ScanService scanService) {
        this.diffService = diffService;
        this.scanRepository= scanRepository;
        this.scanService=scanService;
    }

    @GetMapping("/project/{scanId}")
    public List<FileDiff> compareProjects(@PathVariable String scanId) {

        ScanTask task = scanRepository.findById(scanId);

        if (task == null) {
            throw new RuntimeException("Scan task not found for id: " + scanId);
        }

        String original = task.getProjectPath();
        String preview = original + "_fixed";

        return diffService.compareProjects(original, preview);
    }
    
    @PostMapping("/preview/{scanId}")
    public String previewFixes(@PathVariable String scanId) {

        return scanService.previewFixes(scanId);
    }
    
    
}