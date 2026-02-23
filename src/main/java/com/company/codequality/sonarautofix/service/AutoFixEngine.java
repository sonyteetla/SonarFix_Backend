package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.FixRequest;
import com.company.codequality.sonarautofix.model.FixType;
import com.company.codequality.sonarautofix.strategy.FixStrategy;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class AutoFixEngine {

    private final Map<FixType, FixStrategy> strategyMap = new HashMap<>();
    private final ScanService scanService;

    public AutoFixEngine(List<FixStrategy> strategies,
                         ScanService scanService) {

        for (FixStrategy strategy : strategies) {
            strategyMap.put(strategy.getFixType(), strategy);
        }

        this.scanService = scanService;
    }

    public void applyFixes(List<FixRequest> requests,
                           String projectPath,
                           String projectKey) {

        try {

            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("No fixes provided");
            }

            // Group fixes by file
            Map<String, List<FixRequest>> grouped = new HashMap<>();

            for (FixRequest request : requests) {
                grouped
                        .computeIfAbsent(request.getFilePath(),
                                k -> new ArrayList<>())
                        .add(request);
            }

            for (Map.Entry<String, List<FixRequest>> entry : grouped.entrySet()) {

                String filePath = entry.getKey();
                List<FixRequest> fileFixes = entry.getValue();

                // Sort descending by line to avoid line shifting
                fileFixes.sort((a, b) ->
                        Integer.compare(b.getLine(), a.getLine()));

                CompilationUnit cu =
                        StaticJavaParser.parse(Path.of(filePath));

                for (FixRequest request : fileFixes) {

                    FixType type =
                            FixType.valueOf(request.getFixType());

                    FixStrategy strategy =
                            strategyMap.get(type);

                    if (strategy == null) {
                        throw new IllegalArgumentException(
                                "Unsupported fix type: " + type);
                    }

                    boolean applied =
                            strategy.apply(cu, request.getLine());

                    if (!applied) {
                        throw new IllegalStateException(
                                "Failed to apply fix at line "
                                        + request.getLine());
                    }
                }

                // Write file ONCE per file
                Files.write(Path.of(filePath),
                        cu.toString().getBytes());
            }

            // Re-scan SAME project
            scanService.reScan(projectPath, projectKey);

        } catch (Exception e) {
            throw new RuntimeException("Auto fix failed", e);
        }
    }
}