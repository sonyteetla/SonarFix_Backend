package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.strategy.FixStrategy;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class AutoFixEngine {

    private final Map<FixType, FixStrategy> strategyMap = new HashMap<>();
    private final ScanService scanService;

    public AutoFixEngine(List<FixStrategy> strategies,
                         @Lazy ScanService scanService) {

        for (FixStrategy strategy : strategies) {
            strategyMap.put(strategy.getFixType(), strategy);
        }

        System.out.println("Registered strategies: " + strategyMap.keySet());
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
                        request.getFilePath().isBlank()) continue;

                grouped.computeIfAbsent(request.getFilePath(),
                        k -> new ArrayList<>()).add(request);
            }

            initSymbolSolver(projectPath);

            System.out.println("Incoming fix requests: " + requests.size());

            for (Map.Entry<String, List<FixRequest>> entry : grouped.entrySet()) {

                String filePath = entry.getKey();
                List<FixRequest> fileFixes = entry.getValue();

                fileFixes.sort((a, b) ->
                        Integer.compare(b.getLine(), a.getLine()));

                Path path = Path.of(projectPath, filePath);

                System.out.println("---- FILE DEBUG ----");
                System.out.println("projectPath = " + projectPath);
                System.out.println("filePath = " + filePath);
                System.out.println("resolved path = " + path);
                System.out.println("exists = " + Files.exists(path));

                if (!Files.exists(path)) continue;

                List<String> originalLines = Files.readAllLines(path);

                CompilationUnit cu;

                try {
                    cu = StaticJavaParser.parse(path);
                } catch (Exception e) {
                    continue;
                }

                // ================= APPLY SONAR FIXES =================

                for (FixRequest request : fileFixes) {

                    FixType type;

                    try {
                        type = FixType.valueOf(request.getFixType());
                    } catch (Exception e) {
                        continue;
                    }

                    FixStrategy strategy = strategyMap.get(type);

                    if (strategy == null) continue;

                    try {

                        System.out.println("Running strategy: " + type +
                                " | file=" + filePath +
                                " | line=" + request.getLine());

                        int lineIndex = request.getLine() - 1;

                        String beforeCode = "";

                        if (lineIndex >= 0 && lineIndex < originalLines.size()) {
                            beforeCode = originalLines.get(lineIndex).trim();
                        }

                        boolean applied = strategy.apply(cu, request.getLine());

                        if (applied) {

                            System.out.println("SUCCESS: " + type);

                            totalFixed++;

                            fixReport.put(type,
                                    fixReport.getOrDefault(type, 0) + 1);

                            List<String> modifiedLines =
                                    Arrays.asList(cu.toString().split("\n"));

                            String afterCode = "";

                            if (lineIndex >= 0 && lineIndex < modifiedLines.size()) {
                                afterCode = modifiedLines.get(lineIndex).trim();
                            }

                            if (task != null) {

                                FixExecutionReport report =
                                        new FixExecutionReport(
                                                request.getRuleId(),
                                                filePath,
                                                request.getLine(),
                                                beforeCode,
                                                afterCode,
                                                true,
                                                "Fix applied successfully"
                                        );

                                task.addFixReport(report);
                            }
                        }

                    } catch (Exception ex) {

                        System.out.println("FAILED STRATEGY: " + type);
                        ex.printStackTrace();

                        if (task != null) {
                            task.addSuggestion(
                                    new FixSuggestion(
                                            filePath,
                                            request.getLine(),
                                            request.getFixType(),
                                            "Manual fix required. AutoFix skipped.",
                                            "Review rule: " + request.getFixType()
                                    )
                            );
                        }
                    }
                }

                // ================= SMART AUTO FIX =================

                System.out.println("⚡ Running Smart AutoFix Pattern Detection");

                for (FixStrategy strategy : strategyMap.values()) {

                    if (strategy.getFixType() == null) {
                        continue;
                    }

                    try {

                        boolean applied = strategy.apply(cu, -1);

                        if (applied) {

                            totalFixed++;

                            FixType type = strategy.getFixType();

                            fixReport.put(type,
                                    fixReport.getOrDefault(type, 0) + 1);

                            System.out.println("SMART FIX APPLIED: " + type);
                        }

                    } catch (Exception ex) {

                        System.out.println("SMART FIX FAILED: " + strategy.getFixType());
                        ex.printStackTrace();
                    }
                }

                Files.write(path,
                        cu.toString().getBytes(StandardCharsets.UTF_8));
            }

            // ================= RE-SCAN =================

            try {

                if (scanId != null && !scanId.isBlank()) {
                    scanService.reScan(projectPath, projectKey, scanId);
                } else {
                    scanService.reScan(projectPath, projectKey);
                }

            } catch (Exception e) {
                System.out.println("⚠ Re-scan failed: " + e.getMessage());
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

            System.out.println("\n====== AutoFix Execution Report ======");

            for (Map.Entry<FixType, Integer> entry : fixReport.entrySet()) {
                System.out.println(entry.getKey() + " -> " + entry.getValue() + " fixes");
            }

            System.out.println("Total fixes applied: " + totalFixed);
            System.out.println("======================================\n");

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