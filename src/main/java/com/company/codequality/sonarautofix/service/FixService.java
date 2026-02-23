package com.company.codequality.sonarautofix.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@Service
public class FixService {

    /**
     * Apply fixes on the project copy. 
     * Assumes projectPath is already a copy, do NOT append "_fixed" here.
     */
    public void applyFixes(String projectPath) throws IOException {

        Path projectDir = Paths.get(projectPath);

        if (!Files.exists(projectDir)) {
            throw new IllegalArgumentException("Project path does not exist: " + projectPath);
        }

        // Step 1: Apply fixes ONLY inside the given folder
        Files.walk(projectDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        List<String> lines = Files.readAllLines(path);

                        for (int i = 0; i < lines.size(); i++) {
                            // Example fix: replace System.out.println with logger.info
                            if (lines.get(i).contains("System.out.println")) {
                                lines.set(i, lines.get(i).replace("System.out.println", "logger.info"));
                            }
                        }

                        Files.write(path, lines);

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        System.out.println("Fixes applied to project at: " + projectDir);
    }
}