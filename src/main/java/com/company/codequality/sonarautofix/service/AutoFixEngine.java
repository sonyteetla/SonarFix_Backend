package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.strategy.FixStrategy;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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

            // ================= 🔥 STEP 1: METHOD RENAME =================

            for (FixRequest request : requests) {

                if ("METHOD_RENAME".equals(request.getFixType())) {

                    String oldName = request.getOldMethodName();
                    String newName = request.getNewMethodName();

                    if (oldName == null || newName == null) continue;

                    System.out.println("🚀 METHOD RENAME: "
                            + oldName + " → " + newName);

                    applyMethodRename(projectPath, oldName, newName);

                    totalFixed++;

                    fixReport.put(FixType.METHOD_RENAME,
                            fixReport.getOrDefault(FixType.METHOD_RENAME, 0) + 1);
                }
            }

            // ================= NORMAL FIX FLOW =================

            Map<String, List<FixRequest>> grouped = new HashMap<>();

            for (FixRequest request : requests) {

                if ("METHOD_RENAME".equals(request.getFixType())) continue;

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

                Path path = Path.of(projectPath).resolve(filePath).normalize();

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

                        boolean applied = strategy.apply(cu, request.getLine());

                        if (applied) {

                            totalFixed++;

                            fixReport.put(type,
                                    fixReport.getOrDefault(type, 0) + 1);
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                Files.write(path,
                        cu.toString().getBytes(StandardCharsets.UTF_8));
            }

            // ================= REPORT =================

            if (task != null) {

                Map<String, Integer> reportForUi = new HashMap<>();

                for (Map.Entry<FixType, Integer> entry : fixReport.entrySet()) {
                    reportForUi.put(entry.getKey().name(), entry.getValue());
                }

                task.setFixExecutionReport(reportForUi);
                task.setTotalFixesApplied(totalFixed);
            }

            System.out.println("\n====== AutoFix Report ======");
            System.out.println("Total fixes applied: " + totalFixed);
            System.out.println("===========================\n");

            return totalFixed;

        } catch (Exception e) {
            throw new RuntimeException("Auto fix failed", e);
        }
    }

    // ================= 🔥 METHOD RENAME IMPLEMENTATION =================

    private void applyMethodRename(String projectPath, String oldName, String newName) {

        try {

            Files.walk(Path.of(projectPath))
                    .filter(p ->
                            p.toString().endsWith(".java") &&
                           !p.toString().contains(".git") &&
                           !p.toString().contains("target"))
                    .forEach(file -> {

                        try {

                            String content = Files.readString(file);

                            // Rename method definition
                            content = content.replaceAll(
                                    "(\\b(public|private|protected)?\\s+\\w+\\s+)" + oldName + "\\s*\\(",
                                    "$1" + newName + "("
                            );

                            // Rename method calls
                            content = content.replaceAll(
                                    "\\b" + oldName + "\\s*\\(",
                                    newName + "("
                            );

                            Files.writeString(file, content);

                        } catch (Exception e) {
                            System.out.println("Rename failed for: " + file);
                        }
                    });

        } catch (Exception e) {
            throw new RuntimeException("Method rename failed", e);
        }
    }

    // ================= SYMBOL SOLVER =================

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