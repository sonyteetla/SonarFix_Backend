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

    /**
     * Apply Auto Fixes safely
     */
    public int applyFixes(List<FixRequest> requests,
                          String projectPath,
                          String projectKey,
                          String scanId) {

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("No fixes provided");
        }

        int totalFixed = 0;

        try {

            // Group by file path
            Map<String, List<FixRequest>> grouped = new HashMap<>();

            for (FixRequest request : requests) {
                if (request.getFilePath() == null || request.getFilePath().isBlank()) {
                    continue;
                }
                grouped
                    .computeIfAbsent(request.getFilePath(), k -> new ArrayList<>())
                    .add(request);
            }

            // Process file by file
            for (Map.Entry<String, List<FixRequest>> entry : grouped.entrySet()) {

                String filePath = entry.getKey();
                List<FixRequest> fileFixes = entry.getValue();

                // Safe bottom → top sorting
                fileFixes.sort((a, b) -> {

                    Integer lineA = a.getLine();
                    Integer lineB = b.getLine();

                    int safeA = (lineA == null) ? 0 : lineA;
                    int safeB = (lineB == null) ? 0 : lineB;

                    return Integer.compare(safeB, safeA);
                });

                Path path = Path.of(filePath);

                if (!Files.exists(path)) {
                    System.out.println("⚠ File not found. Skipping: " + filePath);
                    continue;
                }

                CompilationUnit cu = StaticJavaParser.parse(path);

                for (FixRequest request : fileFixes) {

                    // Skip manual issues
                    if (request.getFixType() == null ||
                        request.getFixType().isBlank()) {
                        continue;
                    }

                    Integer line = request.getLine();
                    if (line == null) {
                        continue;
                    }

                    FixType type;
                    try {
                        type = FixType.valueOf(request.getFixType());
                    } catch (Exception e) {
                        System.out.println("⚠ Unknown FixType skipped: "
                                + request.getFixType());
                        continue;
                    }

                    FixStrategy strategy = strategyMap.get(type);

                    if (strategy == null) {
                        System.out.println("⚠ No strategy found for: " + type);
                        continue;
                    }

                    try {
                        boolean applied = strategy.apply(cu, line);
                        if (applied) {
                            totalFixed++;
                        }
                    } catch (Exception ex) {
                        System.out.println("⚠ Fix failed at "
                                + filePath + ":" + line);
                    }
                }

                // Write once after all fixes
                Files.write(
                        path,
                        cu.toString().getBytes(StandardCharsets.UTF_8)
                );
            }

            // Re-scan after fixes
            try {
                if (scanId != null && !scanId.isBlank()) {
                    scanService.reScan(projectPath, projectKey, scanId);
                } else {
                    scanService.reScan(projectPath, projectKey);
                }
            } catch (Exception e) {
                System.out.println("⚠ Re-scan failed (non-critical): "
                        + e.getMessage());
            }

            System.out.println("✔ AutoFix applied: " + totalFixed + " fixes");
            return totalFixed;

        } catch (Exception e) {
            throw new RuntimeException("Auto fix failed", e);
        }
    }
} 