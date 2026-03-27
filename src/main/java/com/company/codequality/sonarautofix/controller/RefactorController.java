package com.company.codequality.sonarautofix.controller;
import org.springframework.web.multipart.MultipartFile;
import com.company.codequality.sonarautofix.model.FileDiff;
import com.company.codequality.sonarautofix.model.ScanTask;
import com.company.codequality.sonarautofix.model.VariableRenameRequest;
import com.company.codequality.sonarautofix.repository.ScanRepository;
import com.company.codequality.sonarautofix.service.ProjectDiffService;
import com.company.codequality.sonarautofix.service.ProjectUploadService;
import com.company.codequality.sonarautofix.service.ProjectRenameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/refactor")
@CrossOrigin("*")
public class RefactorController {

    private final ProjectRenameService projectRenameService;
    private final ScanRepository scanRepository;
    private final ProjectUploadService projectUploadService;
    private final ProjectDiffService projectDiffService;

    public RefactorController(ProjectRenameService projectRenameService,
                              ScanRepository scanRepository,
                              ProjectUploadService projectUploadService,
                              ProjectDiffService projectDiffService) {
        this.projectRenameService = projectRenameService;
        this.scanRepository = scanRepository;
        this.projectUploadService = projectUploadService;
        this.projectDiffService = projectDiffService;
    }

    @PostMapping("/rename-variable")
    public ResponseEntity<List<FileDiff>> renameVariable(@RequestBody VariableRenameRequest request) {
        return processRename(request, "variable");
    }

    @PostMapping("/rename-method")
    public ResponseEntity<List<FileDiff>> renameMethod(@RequestBody VariableRenameRequest request) {
        return processRename(request, "method");
    }

    @PostMapping("/rename-class")
    public ResponseEntity<List<FileDiff>> renameClass(@RequestBody VariableRenameRequest request) {
        try {
            if (request.getScanId() == null || request.getOldName() == null || request.getNewName() == null) {
                return ResponseEntity.badRequest().build();
            }

            ScanTask task = scanRepository.findById(request.getScanId());
            if (task == null) return ResponseEntity.notFound().build();

            String originalPath = task.getProjectPath();
            String copyPath = task.getFixedPath();

            if (copyPath == null || !Files.exists(Paths.get(copyPath))) {
                copyPath = projectUploadService.copyProject(task.getProjectPath());
            }

            String csvPath = copyPath + "/class-mapping.csv";
            String reportDir = "reports/" + request.getScanId();

            // Step 1: Generate class mapping CSV if not exists
            if (!Files.exists(Paths.get(csvPath))) {
                projectRenameService.generateClassListCSV(copyPath, csvPath);
            }

            // Step 2: Rename class (also writes to class-usage.csv)
            projectRenameService.renameClassInProject(copyPath, request.getOldName(), request.getNewName());

            // Step 3: Update mapping CSV + generate final report
            projectRenameService.updateCSV(csvPath, request.getOldName(), request.getNewName());
            projectRenameService.generateFinalReportCSV(
                    csvPath,
                    copyPath + "/class-usage.csv",
                    reportDir + "/final-report.csv"
            );

            task.setFixedPath(copyPath);
            scanRepository.update(task);

            List<FileDiff> diffs = projectDiffService.compareProjects(originalPath, copyPath);
            return ResponseEntity.ok(diffs);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/final-report")
    public ResponseEntity<List<String[]>> getFinalReport(@RequestParam String scanId) {
        ScanTask task = scanRepository.findById(scanId);
        if (task == null) return ResponseEntity.notFound().build();

        String csvPath = "reports/" + scanId + "/final-report.csv";

        if (!Files.exists(Paths.get(csvPath))) {
            return ResponseEntity.ok(List.of());
        }

        List<String[]> data = projectRenameService.readCSV(csvPath);
        return ResponseEntity.ok(data);
    }

    // ================= SHARED RENAME HANDLER =================
    private ResponseEntity<List<FileDiff>> processRename(VariableRenameRequest request, String type) {
        try {
            if (request.getScanId() == null || request.getOldName() == null || request.getNewName() == null) {
                return ResponseEntity.badRequest().build();
            }

            ScanTask task = scanRepository.findById(request.getScanId());
            if (task == null) return ResponseEntity.notFound().build();

            String originalPath = task.getProjectPath();

            // Reuse existing fixed copy if available
            String copyPath = task.getFixedPath();
            if (copyPath == null || !Files.exists(Paths.get(copyPath))) {
                copyPath = projectUploadService.copyProject(originalPath);
            }

            // Perform rename (each method now writes to class-usage.csv internally)
            switch (type) {
                case "variable":
                    projectRenameService.renameVariableInProject(copyPath, request.getOldName(), request.getNewName());
                    break;
                case "method":
                    projectRenameService.renameMethodInProject(copyPath, request.getOldName(), request.getNewName());
                    break;
            }

            // Generate final report CSV for variable/method (no class-mapping.csv needed)
        
            String reportDir = "reports/" + request.getScanId();
         

            	List<String> usageFiles = List.of(
            		    copyPath + "/class-usage.csv",
            		    copyPath + "/method-usage.csv",
            		    copyPath + "/variable-usage.csv"
            		);

            		projectRenameService.generateFinalReportCSVFromMultiple(
            		    usageFiles,
            		    reportDir + "/final-report.csv"
            		);
            

            task.setFixedPath(copyPath);
            scanRepository.update(task);

            List<FileDiff> diffs = projectDiffService.compareProjects(originalPath, copyPath);
            return ResponseEntity.ok(diffs);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/rename-from-csv")
    public ResponseEntity<List<FileDiff>> renameFromCSV(
            @RequestParam("file") MultipartFile file,
            @RequestParam String scanId) {

        try {

            ScanTask task = scanRepository.findById(scanId);
            if (task == null) return ResponseEntity.notFound().build();

            String originalPath = task.getProjectPath();
            String copyPath = task.getFixedPath();

            if (copyPath == null || !Files.exists(Paths.get(copyPath))) {
                copyPath = projectUploadService.copyProject(originalPath);
            }

            // ✅ SAVE uploaded CSV temporarily
            Path tempFile = Files.createTempFile("rename-", ".csv");
            file.transferTo(tempFile.toFile());

            // ✅ CALL SERVICE
            projectRenameService.renameFromCSV(copyPath, tempFile.toString());

            // ✅ GENERATE FINAL REPORT
            List<String> usageFiles = List.of(
                    copyPath + "/class-usage.csv",
                    copyPath + "/method-usage.csv",
                    copyPath + "/variable-usage.csv"
            );

            String reportDir = "reports/" + scanId;

            projectRenameService.generateFinalReportCSVFromMultiple(
                    usageFiles,
                    reportDir + "/final-report.csv"
            );

            task.setFixedPath(copyPath);
            scanRepository.update(task);

            List<FileDiff> diffs =
                    projectDiffService.compareProjects(originalPath, copyPath);

            return ResponseEntity.ok(diffs);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/rename/classes-csv")
    public ResponseEntity<List<FileDiff>> renameClassesCSV(
            @RequestParam("file") MultipartFile file,
            @RequestParam String scanId) {

        return processCSVByType(file, scanId, "class");
    }

    @PostMapping("/rename/methods-csv")
    public ResponseEntity<List<FileDiff>> renameMethodsCSV(
            @RequestParam("file") MultipartFile file,
            @RequestParam String scanId) {

        return processCSVByType(file, scanId, "method");
    }

    @PostMapping("/rename/variables-csv")
    public ResponseEntity<List<FileDiff>> renameVariablesCSV(
            @RequestParam("file") MultipartFile file,
            @RequestParam String scanId) {

        return processCSVByType(file, scanId, "variable");
    }
    
    private ResponseEntity<List<FileDiff>> processCSVByType(
            MultipartFile file,
            String scanId,
            String type) {

        try {

            ScanTask task = scanRepository.findById(scanId);
            if (task == null) return ResponseEntity.notFound().build();

            String originalPath = task.getProjectPath();
            String copyPath = task.getFixedPath();

            if (copyPath == null || !Files.exists(Paths.get(copyPath))) {
                copyPath = projectUploadService.copyProject(originalPath);
            }

            Path tempFile = Files.createTempFile("rename-", ".csv");
            file.transferTo(tempFile.toFile());

            // ✅ CALL NEW METHOD
            projectRenameService.renameFromCSVByType(copyPath, tempFile.toString(), type);

            // ✅ REPORT
            List<String> usageFiles = List.of(
                    copyPath + "/class-usage.csv",
                    copyPath + "/method-usage.csv",
                    copyPath + "/variable-usage.csv"
            );

            String reportDir = "reports/" + scanId;

            projectRenameService.generateFinalReportCSVFromMultiple(
                    usageFiles,
                    reportDir + "/final-report.csv"
            );

            task.setFixedPath(copyPath);
            scanRepository.update(task);

            List<FileDiff> diffs =
                    projectDiffService.compareProjects(originalPath, copyPath);

            return ResponseEntity.ok(diffs);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private ResponseEntity<byte[]> downloadCSV(String scanId, String fileName) {
        try {
            ScanTask task = scanRepository.findById(scanId);
            if (task == null) return ResponseEntity.notFound().build();

            String projectPath = task.getProjectPath();

            Path filePath = Paths.get(projectPath, fileName);

            // 🔴 CRITICAL: If file not exists → generate it
            if (!Files.exists(filePath)) {

                switch (fileName) {
                    case "class-list.csv":
                        projectRenameService.generateClassListCSV(projectPath, filePath.toString());
                        break;

                    case "method-list.csv":
                        projectRenameService.generateMethodListCSV(projectPath, filePath.toString());
                        break;

                    case "variable-list.csv":
                        projectRenameService.generateVariableListCSV(projectPath, filePath.toString());
                        break;
                }
            }

            byte[] data = Files.readAllBytes(filePath);

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + fileName)
                    .header("Content-Type", "text/csv")
                    .body(data);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/export/classes/{scanId}")
    public ResponseEntity<byte[]> downloadClassCSV(@PathVariable String scanId) {
        return downloadCSV(scanId, "class-list.csv");
    }

    @GetMapping("/export/methods/{scanId}")
    public ResponseEntity<byte[]> downloadMethodCSV(@PathVariable String scanId) {
        return downloadCSV(scanId, "method-list.csv");
    }

    @GetMapping("/export/variables/{scanId}")
    public ResponseEntity<byte[]> downloadVariableCSV(@PathVariable String scanId) {
        return downloadCSV(scanId, "variable-list.csv");
    }
}