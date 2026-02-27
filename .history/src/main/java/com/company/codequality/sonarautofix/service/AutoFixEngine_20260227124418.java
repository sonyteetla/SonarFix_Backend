package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.repository.FixRecordRepository;
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
    private final FixRecordRepository fixRecordRepository;

    public AutoFixEngine(List<FixStrategy> strategies,
                         @Lazy ScanService scanService,
                         FixRecordRepository fixRecordRepository) {

        for (FixStrategy strategy : strategies) {
            strategyMap.put(strategy.getFixType(), strategy);
        }

        this.scanService = scanService;
        this.fixRecordRepository = fixRecordRepository;
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

            // Group fixes by file
            Map<String, List<FixRequest>> grouped = new HashMap<>();

            for (FixRequest request : requests) {

                if (request.getFilePath() == null ||
                        request.getFilePath().isBlank() ||
                        request.getLine() <= 0) {
                    continue;
                }

                grouped.computeIfAbsent(request.getFilePath(),
                        k -> new ArrayList<>()).add(request);
            }

            for (Map.Entry<String, List<FixRequest>> entry : grouped.entrySet()) {

                String filePath = entry.getKey();
                List<FixRequest> fileFixes = entry.getValue();

                // Sort bottom → top to avoid line shift issues
                fileFixes.sort((a, b) ->
                        Integer.compare(b.getLine(), a.getLine()));

                Path path = Path.of(projectPath, filePath);

                if (!Files.exists(path)) {
                    System.out.println("⚠ File not found: " + path);
                    continue;
                }

                CompilationUnit cu;
                try {
                    cu = StaticJavaParser.parse(path);
                } catch (Exception e) {
                    System.out.println("⚠ Failed to parse file: " + path);
                    continue;
                }

                for (FixRequest request : fileFixes) {

                    FixType type;
                    try {
                        type = FixType.valueOf(request.getFixType());
                    } catch (Exception e) {
                        System.out.println("⚠ Invalid FixType: " + request.getFixType());
                        continue;
                    }

                    FixStrategy strategy = strategyMap.get(type);
                    if (strategy == null) {
                        System.out.println("⚠ No strategy for: " + type);
                        continue;
                    }

                    try {

                        boolean applied =
                                strategy.apply(cu, request.getLine());

                        if (applied) {

                            totalFixed++;
                            fixReport.put(type,
                                    fixReport.getOrDefault(type, 0) + 1);

                            FixRecord record = FixRecord.builder()
                                    .projectKey(projectKey)
                                    .filePath(filePath)
                                    .line(request.getLine())
                                    .fixType(type.name())
                                    .fixedAt(java.time.LocalDateTime.now())
                                    .build();

                            fixRecordRepository.save(record);

                        } else {
                            System.out.println("⚠ Fix not applied at line "
                                    + request.getLine());
                        }

                    } catch (Exception ex) {

                        System.out.println("⚠ Fix failed at line "
                                + request.getLine() + " in " + filePath);

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

                // Write modified file
                try {
                    Files.write(path,
                            cu.toString().getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    System.out.println("⚠ Failed to write file: " + path);
                }
            }

            // Store execution report before re-scan
            if (task != null) {

                Map<String, Integer> reportForUi = new HashMap<>();

                for (Map.Entry<FixType, Integer> entry : fixReport.entrySet()) {
                    reportForUi.put(entry.getKey().name(), entry.getValue());
                }

                task.setFixExecutionReport(reportForUi);
                task.setTotalFixesApplied(totalFixed);
            }

            // Re-scan
            try {
            	if (scanId == null || scanId.isBlank()) {
            	    throw new IllegalStateException("scanId required for re-scan");
            	}

            	scanService.reScan(projectPath, projectKey, scanId);
            	
            } catch (Exception e) {
                System.out.println("⚠ Re-scan failed (non-critical): " + e.getMessage());
            }

            System.out.println("\n====== AutoFix Execution Report ======");
            fixReport.forEach((k, v) ->
                    System.out.println(k + " -> " + v + " fixes"));
            System.out.println("Total fixes applied: " + totalFixed);
            System.out.println("======================================\n");

            return totalFixed;

        } catch (Exception e) {
            throw new RuntimeException("Auto fix failed", e);
        }
    }
}
