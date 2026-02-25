package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
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

            for (Map.Entry<String, List<FixRequest>> entry : grouped.entrySet()) {

                String filePath = entry.getKey();
                List<FixRequest> fileFixes = entry.getValue();

                fileFixes.sort((a, b) ->
                        Integer.compare(b.getLine(), a.getLine()));

                Path path = Path.of(projectPath, filePath);
                if (!Files.exists(path)) continue;

                CompilationUnit cu;

                try {
                    cu = StaticJavaParser.parse(path);
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
                    if (strategy == null) continue;

                    try {
                        boolean applied =
                                strategy.apply(cu, request.getLine());

                        if (applied) {
                            totalFixed++;
                            fixReport.put(type,
                                    fixReport.getOrDefault(type, 0) + 1);
                        }

                    } catch (Exception ex) {

                        if (task != null) {
                            task.addSuggestion(
                                    new FixSuggestion(
                                            filePath,
                                            request.getLine(),
                                            type.name(),
                                            "Manual fix required. AutoFix skipped to prevent compile error.",
                                            "Review rule: " + type.name()
                                    )
                            );
                        }
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
                System.out.println("⚠ Re-scan failed (non-critical): " + e.getMessage());
            }

            // ================= STORE EXECUTION REPORT FOR UI =================
            if (task != null) {
                Map<String, Integer> reportForUi = new HashMap<>();

                for (Map.Entry<FixType, Integer> entry : fixReport.entrySet()) {
                    reportForUi.put(entry.getKey().name(), entry.getValue());
                }

                task.setFixExecutionReport(reportForUi);
                task.setTotalFixesApplied(totalFixed);
            }

            // ================= CONSOLE REPORT =================
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
}