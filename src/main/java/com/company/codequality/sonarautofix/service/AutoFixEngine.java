package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.FixRequest;
import com.company.codequality.sonarautofix.model.FixType;
import com.company.codequality.sonarautofix.strategy.FixStrategy;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

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

        try {

            // Group by file
            Map<String, List<FixRequest>> grouped = new HashMap<>();

            for (FixRequest request : requests) {
                if (request.getFilePath() == null || request.getFilePath().isBlank()) {
                    continue;
                }
                grouped.computeIfAbsent(request.getFilePath(),
                        k -> new ArrayList<>()).add(request);
            }

            for (Map.Entry<String, List<FixRequest>> entry : grouped.entrySet()) {

                String filePath = entry.getKey();
                List<FixRequest> fileFixes = entry.getValue();

                // Bottom → Top sorting (no null checks needed)
                fileFixes.sort((a, b) ->
                        Integer.compare(b.getLine(), a.getLine()));

                Path path;

                if (Path.of(filePath).isAbsolute()) {
                    path = Path.of(filePath);
                } else {
                    path = Path.of(projectPath, filePath);
                }

                if (!Files.exists(path)) {
                    System.out.println("⚠ File not found. Skipping: " + path);
                    continue;
                }

                CompilationUnit cu;

                try {
                    cu = StaticJavaParser.parse(path);
                } catch (Exception parseError) {
                    System.out.println("❌ JavaParser failed for: " + path);
                    parseError.printStackTrace();
                    continue;
                }

                for (FixRequest request : fileFixes) {

                    if (request.getFixType() == null || request.getFixType().isBlank()) {
                        continue;
                    }

                    FixType type;
                    try {
                        type = FixType.valueOf(request.getFixType());
                    } catch (Exception e) {
                        System.out.println("⚠ Unknown FixType skipped: " + request.getFixType());
                        continue;
                    }

                    FixStrategy strategy = strategyMap.get(type);

                    if (strategy == null) {
                        System.out.println("⚠ No strategy found for: " + type);
                        continue;
                    }

                    try {
                        boolean applied = strategy.apply(cu, request.getLine());

                        if (applied) {
                            totalFixed++;
                            fixReport.put(type,
                                    fixReport.getOrDefault(type, 0) + 1);
                        }

                    } catch (Exception ex) {
                        System.out.println("⚠ Fix failed at " + path + ":" + request.getLine());
                        ex.printStackTrace();
                    }
                }

                try {
                    Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
                } catch (Exception writeError) {
                    System.out.println("❌ Failed writing file: " + path);
                    writeError.printStackTrace();
                }
            }

            // Re-scan
            try {
                if (scanId != null && !scanId.isBlank()) {
                    scanService.reScan(projectPath, projectKey, scanId);
                } else {
                    scanService.reScan(projectPath, projectKey);
                }
            } catch (Exception e) {
                System.out.println("⚠ Re-scan failed (non-critical): " + e.getMessage());
            }

            // Execution Report
            System.out.println("\n====== AutoFix Execution Report ======");
            for (Map.Entry<FixType, Integer> entry : fixReport.entrySet()) {
                System.out.println(entry.getKey() + " -> " + entry.getValue() + " fixes");
            }
            System.out.println("Total fixes applied: " + totalFixed);
            System.out.println("======================================\n");

            return totalFixed;

        } catch (Exception e) {

            System.out.println("\n❌ AUTO FIX CRASHED");
            e.printStackTrace();
            System.out.println("❌ END ERROR\n");

            throw new RuntimeException("Auto fix failed: " + e.getMessage(), e);
        }
    }
}