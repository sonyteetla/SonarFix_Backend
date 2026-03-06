package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.strategy.FixStrategy;
import com.company.codequality.sonarautofix.util.ProjectZipUtil;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class AutoFixEngine {

    private static final Logger log = LoggerFactory.getLogger(AutoFixEngine.class);

    private final Map<FixType, FixStrategy> strategyMap = new HashMap<>();
    private final ScanService scanService;

    public AutoFixEngine(List<FixStrategy> strategies,
            @Lazy ScanService scanService) {

        for (FixStrategy strategy : strategies) {
            strategyMap.put(strategy.getFixType(), strategy);
        }
        log.info("Registered strategies: {}", strategyMap.keySet());
        this.scanService = scanService;
    }

    public int applyFixes(List<FixRequest> requests,
            String projectPath,
            String projectKey,
            String scanId) {

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("No fixes provided");
        }

        int totalFixed = 0;
        Map<FixType, Integer> fixReport = new HashMap<>();

        ScanTask task = null;
        if (scanId != null && !scanId.isBlank()) {
            task = scanService.getScanTask(scanId);
        }

        try {

            Map<String, List<FixRequest>> grouped = new HashMap<>();

            for (FixRequest request : requests) {
                if (request.getFilePath() == null ||
                        request.getFilePath().isBlank())
                    continue;

                grouped.computeIfAbsent(request.getFilePath(),
                        k -> new ArrayList<>()).add(request);
            }

            initSymbolSolver(projectPath);
            log.info("Processing {} fix request(s) across {} file(s)", requests.size(), grouped.size());
            for (Map.Entry<String, List<FixRequest>> entry : grouped.entrySet()) {

                String filePath = entry.getKey();
                List<FixRequest> fileFixes = entry.getValue();
                fileFixes.sort((a, b) -> Integer.compare(b.getLine(), a.getLine()));

                Path path = Path.of(projectPath, filePath);
                log.debug("Applying fixes to file: {} (exists={})", path, Files.exists(path));
                if (!Files.exists(path))
                    continue;

                List<String> originalLines = Files.readAllLines(path);

                CompilationUnit cu;
                try {
                    cu = StaticJavaParser.parse(path);
                    LexicalPreservingPrinter.setup(cu);
                } catch (Exception e) {
                    continue;
                }

                for (FixRequest request : fileFixes) {

                    FixType type;
                    try {
                        type = FixType.valueOf(request.getFixType());
                    } catch (Exception e) {
                        continue;
                    }

                    FixStrategy strategy = strategyMap.get(type);
                    if (strategy == null)
                        continue;

                    try {

                        int lineIndex = request.getLine() - 1;

                        String beforeCode = "";
                        if (lineIndex >= 0 && lineIndex < originalLines.size()) {
                            beforeCode = originalLines.get(lineIndex).trim();
                        }

                        boolean applied = strategy.apply(cu, request.getLine());

                        if (applied) {

                            totalFixed++;
                            fixReport.put(type,
                                    fixReport.getOrDefault(type, 0) + 1);

                            List<String> modifiedLines = Arrays.asList(LexicalPreservingPrinter.print(cu).split("\\R"));

                            String afterCode = "";
                            if (lineIndex >= 0 && lineIndex < modifiedLines.size()) {
                                afterCode = modifiedLines.get(lineIndex).trim();
                            }

                            if (task != null) {

                                FixExecutionReport report = new FixExecutionReport(
                                        request.getRuleId(),
                                        filePath,
                                        request.getLine(),
                                        beforeCode,
                                        afterCode,
                                        true,
                                        "Fix applied successfully");

                                task.addFixReport(report);
                            }
                        }

                    } catch (Exception ex) {

                        if (task != null) {
                            task.addSuggestion(
                                    new FixSuggestion(
                                            filePath,
                                            request.getLine(),
                                            request.getFixType(),
                                            "Manual fix required. AutoFix skipped.",
                                            "Review rule: " + request.getFixType()));
                        }
                    }
                }

                Files.write(path,
                        LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }

            // ================= ZIP REFACTORED PROJECT =================
            // Always zip after fixes so GET /api/fix/download/{scanId} works
            // regardless of whether this was triggered by Fix-All or selective fix.
            try {
                String zipPath = ProjectZipUtil.zipProject(projectPath);
                log.info("Refactored project zipped at: {}", zipPath);
            } catch (Exception e) {
                log.warn("Could not zip refactored project: {}", e.getMessage());
            }

            // ================= RE-SCAN =================
            try {
                if (scanId != null && !scanId.isBlank()) {
                    scanService.reScan(projectPath, projectKey, scanId);
                } else {
                    scanService.reScan(projectPath, projectKey);
                }
            } catch (Exception e) {
                log.warn("Re-scan failed: {}", e.getMessage());
            }

            // ================= STORE SUMMARY =================
            if (task != null) {

                Map<String, Integer> reportForUi = new HashMap<>();

                for (Map.Entry<FixType, Integer> entry : fixReport.entrySet()) {
                    reportForUi.put(entry.getKey().name(), entry.getValue());
                }

                task.setFixExecutionReport(reportForUi);
                task.setTotalFixesApplied(totalFixed);
            }

            log.info("====== AutoFix Execution Report ======");
            for (Map.Entry<FixType, Integer> entry : fixReport.entrySet()) {
                log.info("{} -> {} fix(es)", entry.getKey(), entry.getValue());
            }
            log.info("Total fixes applied: {}", totalFixed);

            return totalFixed;

        } catch (Exception e) {
            throw new RuntimeException("Auto fix failed", e);
        }
    }

    private void initSymbolSolver(String projectPath) {

        CombinedTypeSolver solver = new CombinedTypeSolver();

        solver.add(new ReflectionTypeSolver());

        File srcMainJava = new File(projectPath, "src/main/java");

        if (srcMainJava.exists()) {
            solver.add(new JavaParserTypeSolver(srcMainJava));
        }

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(solver);

        StaticJavaParser.getConfiguration()
                .setSymbolResolver(symbolSolver);
    }
}