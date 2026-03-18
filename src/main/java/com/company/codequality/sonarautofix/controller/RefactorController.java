package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.FileDiff;
import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.model.VariableRenameRequest;
import com.company.codequality.sonarautofix.repository.ScanRepository;
import com.company.codequality.sonarautofix.service.ProjectDiffService;
import com.company.codequality.sonarautofix.service.ProjectUploadService;
import com.company.codequality.sonarautofix.service.VariableRenameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/refactor")
@CrossOrigin("*")
public class RefactorController {

    private final VariableRenameService variableRenameService;
    private final ScanRepository scanRepository;
    private final ProjectUploadService projectUploadService;
    private final ProjectDiffService projectDiffService;

    public RefactorController(VariableRenameService variableRenameService,
                              ScanRepository scanRepository,
                              ProjectUploadService projectUploadService,
                              ProjectDiffService projectDiffService) {
        this.variableRenameService = variableRenameService;
        this.scanRepository = scanRepository;
        this.projectUploadService = projectUploadService;
        this.projectDiffService = projectDiffService;
    }

    @PostMapping("/rename-variable")
    public ResponseEntity<List<FileDiff>> renameVariable(@RequestBody VariableRenameRequest request) {
        try {
            if (request.getScanId() == null || request.getOldName() == null || request.getNewName() == null) {
                return ResponseEntity.badRequest().build();
            }

            ScanTask task = scanRepository.findById(request.getScanId());
            if (task == null) {
                return ResponseEntity.notFound().build();
            }

            String originalPath = task.getProjectPath();
            String copyPath = projectUploadService.copyProject(originalPath);

            // Use JavaParser AST via service to update renaming modifications and save files
            variableRenameService.renameVariableInProject(copyPath, request.getOldName(), request.getNewName());

            // Persist the fixed path so it's not lost
            task.setFixedPath(copyPath);
            scanRepository.update(task);

            // Return diff lines as response
            List<FileDiff> diffs = projectDiffService.compareProjects(originalPath, copyPath);
            return ResponseEntity.ok(diffs);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
