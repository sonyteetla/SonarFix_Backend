package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.dto.FileDifference;
import com.company.codequality.sonarautofix.dto.LineDifference;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class DifferenceService {

    /**
     * Compare original and fixed projects and return structured differences.
     *
     * @param originalPath path to the original project
     * @param fixedPath    path to the fixed project (_fixed folder)
     * @return List of FileDifference objects
     * @throws IOException
     */
    public List<FileDifference> compareProjects(String originalPath, String fixedPath) throws IOException {

        List<FileDifference> diffResult = new ArrayList<>();

        // Walk through all Java files in original project
        Files.walk(Paths.get(originalPath))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(originalFile -> {
                    try {
                        // Relative path for file
                        Path relativePath = Paths.get(originalPath).relativize(originalFile);

                        // Corresponding fixed file
                        Path fixedFile = Paths.get(fixedPath).resolve(relativePath);

                        if (Files.exists(fixedFile)) {

                            List<String> originalLines = Files.readAllLines(originalFile);
                            List<String> fixedLines = Files.readAllLines(fixedFile);

                            List<LineDifference> lineDiffs = new ArrayList<>();

                            int maxLines = Math.max(originalLines.size(), fixedLines.size());

                            for (int i = 0; i < maxLines; i++) {
                                String originalLine = i < originalLines.size() ? originalLines.get(i) : "";
                                String modifiedLine = i < fixedLines.size() ? fixedLines.get(i) : "";

                                if (!originalLine.equals(modifiedLine)) {
                                    lineDiffs.add(new LineDifference(i + 1, originalLine, modifiedLine));
                                }
                            }

                            if (!lineDiffs.isEmpty()) {
                                diffResult.add(new FileDifference(
                                        originalFile.getFileName().toString(),
                                        relativePath.toString().replace("\\", "/"), // normalize path
                                        lineDiffs
                                ));
                            }
                        }

                    } catch (IOException e) {
                        throw new RuntimeException("Failed to compare file: " + originalFile, e);
                    }
                });

        return diffResult;
    }
}