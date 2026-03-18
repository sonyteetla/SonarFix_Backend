package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.repository.FixRecordRepository;
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
import java.time.LocalDateTime;
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

        System.out.println("Registered strategies: " + strategyMap.keySet());
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
                if (request.getFilePath() == null || request.getFilePath().isBlank()) continue;

                grouped.computeIfAbsent(request.getFilePath(),
                        k -> new ArrayList<>()).add(request);
            }

            initSymbolSolver(projectPath);

            for (Map.Entry<String, List<FixRequest>> entry : grouped.entrySet()) {

                String filePath = entry.getKey();
                List<FixRequest> fileFixes = entry.getValue();

                fileFixes.sort((a, b) -> Integer.compare(b.getLine(), a.getLine()));

                Path path = Path.of(projectPath).resolve(filePath).normalize();

                System.out.println("Resolving fix path: " + path + " | exists=" + Files.exists(path));

                if (!Files.exists(path)) {
                    System.err.println("FILE NOT FOUND: " + path);
                    continue;
                }

                List<String> originalLines = Files.readAllLines(path);

                CompilationUnit cu;

                try {
                    cu = StaticJavaParser.parse(path);
                    com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter.setup(cu);
                } catch (Exception e) {
                    System.err.println("Failed to parse " + path + ": " + e.getMessage());
                    continue;
                }

                // ================= APPLY REQUESTED FIXES =================

                for (FixRequest request : fileFixes) {

                    FixType type;

                    try {
                        type = FixType.valueOf(request.getFixType());
                    } catch (Exception e) {
                        continue;
                    }

                    FixStrategy strategy = strategyMap.get(type);

                    System.out.println("Applying strategy " + type + " for file " + filePath + " at line " + request.getLine() + " | strategy found=" + (strategy != null));

                    if (strategy == null) continue;

                    try {

                        int lineIndex = request.getLine() - 1;

                        String beforeCode = "";

                        if (lineIndex >= 0 && lineIndex < originalLines.size()) {
                            beforeCode = originalLines.get(lineIndex).trim();
                        }

                        boolean applied = strategy.apply(cu, request.getLine(), projectPath);

                        System.out.println("Strategy " + type + " applied=" + applied + " | beforeCode='" + beforeCode + "'");
                        if (applied) {

                            totalFixed++;

                            fixReport.put(type,
                                    fixReport.getOrDefault(type, 0) + 1);

                            String currentFullCode = com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter.print(cu);
                            String[] modifiedLinesArr = currentFullCode.split("\\R");

                            String afterCode = "";

                            if (lineIndex >= 0 && lineIndex < modifiedLinesArr.length) {
                                afterCode = modifiedLinesArr[lineIndex].trim();
                            }

                            // ================= STORE FIX RECORD =================

                            FixRecord record = new FixRecord();

                            record.setProjectKey(projectKey);
                            record.setFilePath(filePath);
                           
                            record.setFixType(type.name());
                          

                            fixRecordRepository.save(record);

                            // ================= TASK REPORT =================

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
                        System.err.println("Strategy " + strategy.getFixType() + " failed on line " + request.getLine() + ": " + ex.getMessage());
                        if (task != null) {

                            task.addSuggestion(
                                    new FixSuggestion(
                                            filePath,
                                            request.getLine(),
                                            request.getFixType(),
                                            "Manual fix required. AutoFix skipped. Error: " + ex.getMessage(),
                                            "Review rule: " + request.getFixType()
                                    )
                            );
                        }
                    }
                }


                // ================= SMART AUTO FIX =================
                // Only run smart fix if NO specific line requests were provided
                // (i.e., this is a full "Fix All" mode, not a targeted fix)
                if (fileFixes.stream().allMatch(r -> r.getLine() <= 0)) {
                    for (FixStrategy strategy : strategyMap.values()) {

                        if (strategy.getFixType() == null) continue;

                        try {

                            boolean applied = strategy.apply(cu, -1, projectPath);

                            if (applied) {

                                totalFixed++;

                                FixType type = strategy.getFixType();

                                fixReport.put(type,
                                        fixReport.getOrDefault(type, 0) + 1);

                                FixRecord record = new FixRecord();

                                record.setProjectKey(projectKey);
                                record.setFilePath(filePath);

                                record.setFixType(type.name());

                                fixRecordRepository.save(record);

                                System.out.println("SMART FIX APPLIED: " + type);
                            }

                        } catch (Exception ex) {
                            System.out.println("SMART FIX FAILED: " + strategy.getFixType());
                        }
                    }
                }

                String codeToWrite = com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter.print(cu);
                Files.write(path, codeToWrite.getBytes(StandardCharsets.UTF_8));
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

            System.out.println("Total fixes applied: " + totalFixed);

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