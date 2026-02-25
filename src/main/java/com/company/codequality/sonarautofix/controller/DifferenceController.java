package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.dto.FileDifference;
import com.company.codequality.sonarautofix.service.DifferenceService;
import com.company.codequality.sonarautofix.service.FixService;
import com.company.codequality.sonarautofix.service.ProjectUploadService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/diff")

public class DifferenceController {

    private final FixService fixService;
    private final DifferenceService differenceService;
    private final ProjectUploadService projectUploadService;

    // Constructor Injection (BEST PRACTICE)
    public DifferenceController(FixService fixService,
            DifferenceService differenceService,
            ProjectUploadService projectUploadService) {
        this.fixService = fixService;
        this.differenceService = differenceService;
        this.projectUploadService = projectUploadService;
    }

    /**
     * Compare original project with fixed version and return structured differences
     *
     * @param projectPath path to the project folder
     * @return List of FileDifference objects (ready for frontend viewer)
     * @throws Exception in case of file IO errors
     */
    @PostMapping("/compare")
    public List<FileDifference> compare(@RequestParam("projectPath") String projectPath) throws Exception {

        // 1️⃣ Copy project to a fixed folder (avoid _fixed duplication)
        String fixedPath = projectUploadService.copyProject(projectPath);

        // 2️⃣ Apply fixes ONLY to the copied folder
        fixService.applyFixes(fixedPath);

        // 3️⃣ Compare original vs fixed and return structured differences
        return differenceService.compareProjects(projectPath, fixedPath);
    }
}