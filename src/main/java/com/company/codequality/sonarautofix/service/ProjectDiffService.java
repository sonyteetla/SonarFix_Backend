package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.DiffLine;
import com.company.codequality.sonarautofix.model.FileDiff;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.*;

@Service
public class ProjectDiffService {

    public List<FileDiff> compareProjects(String originalDir, String fixedDir) {

        List<FileDiff> result = new ArrayList<>();

        try {

            Path originalRoot = Paths.get(originalDir);
            Path fixedRoot = Paths.get(fixedDir);

            Files.walk(originalRoot)
                    .filter(Files::isRegularFile)

                    // ✅ Only Java files
                    .filter(p -> p.toString().endsWith(".java"))

                    // ✅ Skip unwanted folders
                    .filter(p ->
                            !p.toString().contains(".git") &&
                            !p.toString().contains("target") &&
                            !p.toString().contains("node_modules") &&
                            !p.toString().contains("build"))

                    .forEach(originalFile -> {

                        try {

                            // 🔥 CRITICAL: RELATIVE PATH MATCHING
                            Path relativePath = originalRoot.relativize(originalFile);
                            Path fixedFile = fixedRoot.resolve(relativePath);

                            if (!Files.exists(fixedFile)) return;

                            List<String> originalLines = Files.readAllLines(originalFile);
                            List<String> fixedLines = Files.readAllLines(fixedFile);

                            // 🔥 Skip if identical
                            if (originalLines.equals(fixedLines)) return;

                            List<DiffLine> diffLines = new ArrayList<>();

                            int max = Math.max(originalLines.size(), fixedLines.size());

                            for (int i = 0; i < max; i++) {

                                String o = i < originalLines.size() ? originalLines.get(i) : null;
                                String m = i < fixedLines.size() ? fixedLines.get(i) : null;

                                // ✅ Skip unchanged lines (IMPORTANT for UI clarity)
                                if (Objects.equals(o, m)) continue;

                                String type;

                                if (o == null) {
                                    type = "ADDED";
                                } else if (m == null) {
                                    type = "REMOVED";
                                } else {
                                    type = "MODIFIED";
                                }

                                diffLines.add(new DiffLine(
                                        i + 1,
                                        o,
                                        m,
                                        type
                                ));
                            }

                            if (!diffLines.isEmpty()) {
                                result.add(new FileDiff(
                                        relativePath.toString(),
                                        diffLines
                                ));
                            }

                        } catch (Exception e) {
                            System.err.println("❌ Diff failed for file: " + originalFile);
                            e.printStackTrace();
                        }
                    });

        } catch (Exception e) {
            throw new RuntimeException("❌ Project diff failed", e);
        }

        System.out.println("🔥 TOTAL DIFF FILES: " + result.size());

        return result;
    }
}