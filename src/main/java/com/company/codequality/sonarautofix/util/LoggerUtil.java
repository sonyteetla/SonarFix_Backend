package com.company.codequality.sonarautofix.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LoggerUtil {

    /**
     * Ensures an SLF4J logger field exists in the compilation unit.
     * Also adds the SLF4J dependency to the project's pom.xml if needed.
     *
     * @param cu          the compilation unit to modify
     * @param projectPath the base path of the project (to locate pom.xml)
     */
    public static void ensureSlf4jLoggerExists(CompilationUnit cu, String projectPath) {

        List<ClassOrInterfaceDeclaration> classes =
                cu.findAll(ClassOrInterfaceDeclaration.class);

        if (classes.isEmpty()) return;

        ClassOrInterfaceDeclaration clazz = classes.get(0);

        // If there's already a 'log' or 'logger' field of any Logger type, skip
        if (loggerFieldAlreadyExists(clazz)) {
            // Still fix imports to point to SLF4J only if we're adding it
            return;
        }

        // Check if java.util.logging.Logger import already exists (pre-existing logger set up)
        boolean hasJUL = hasImport(cu, "java.util.logging.Logger")
                        || hasImport(cu, "java.util.logging.LogManager");

        if (hasJUL) {
            // The class uses java.util.logging — replace calls but keep JUL-compatible
            // Remove the JUL import and replace with SLF4J
            removeImport(cu, "java.util.logging.Logger");
            removeImport(cu, "java.util.logging.LogManager");
        }

        ensureImport(cu, "org.slf4j.Logger");
        ensureImport(cu, "org.slf4j.LoggerFactory");

        FieldDeclaration loggerField =
                StaticJavaParser.parseBodyDeclaration(
                        "private static final Logger log = " +
                        "LoggerFactory.getLogger(" +
                        clazz.getNameAsString() + ".class);"
                ).asFieldDeclaration();

        clazz.getMembers().addFirst(loggerField);

        // Add SLF4J dependency to pom.xml if projectPath is provided
        if (projectPath != null && !projectPath.isBlank()) {
            ensureSlf4jInPom(projectPath);
        }
    }

    /**
     * Backwards compatible version without projectPath.
     */
    public static void ensureSlf4jLoggerExists(CompilationUnit cu) {
        ensureSlf4jLoggerExists(cu, null);
    }

    private static boolean loggerFieldAlreadyExists(ClassOrInterfaceDeclaration clazz) {

        for (FieldDeclaration field : clazz.getFields()) {

            String type = field.getElementType().asString();

            if ("Logger".equals(type)
                    || "org.slf4j.Logger".equals(type)
                    || "java.util.logging.Logger".equals(type)) {

                // Check if there's already a field named 'log' or 'logger'
                return field.getVariables().stream()
                        .anyMatch(v -> {
                            String name = v.getNameAsString().toLowerCase();
                            return name.equals("log") || name.equals("logger");
                        });
            }
        }
        return false;
    }

    private static boolean hasImport(CompilationUnit cu, String importName) {
        return cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals(importName));
    }

    private static void removeImport(CompilationUnit cu, String importName) {
        cu.getImports().removeIf(imp -> imp.getNameAsString().equals(importName));
    }

    private static void ensureImport(CompilationUnit cu, String importName) {

        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(importName)) {
                return;
            }
        }

        cu.addImport(importName);
    }

    /**
     * Adds the SLF4J API dependency to the project's pom.xml if it's not already present.
     */
    private static void ensureSlf4jInPom(String projectPath) {

        Path pomPath = Path.of(projectPath).resolve("pom.xml");

        if (!Files.exists(pomPath)) {
            return; // No pom.xml — skip
        }

        try {

            String content = Files.readString(pomPath, StandardCharsets.UTF_8);

            if (content.contains("slf4j-api") || content.contains("slf4j")) {
                return; // Already has SLF4J
            }

            String slf4jDep =
                    "        <dependency>\n" +
                    "            <groupId>org.slf4j</groupId>\n" +
                    "            <artifactId>slf4j-api</artifactId>\n" +
                    "            <version>2.0.9</version>\n" +
                    "        </dependency>\n" +
                    "        <dependency>\n" +
                    "            <groupId>org.slf4j</groupId>\n" +
                    "            <artifactId>slf4j-simple</artifactId>\n" +
                    "            <version>2.0.9</version>\n" +
                    "        </dependency>\n";

            // Insert before </dependencies> tag
            if (content.contains("</dependencies>")) {

                String updated = content.replace(
                        "</dependencies>",
                        slf4jDep + "    </dependencies>"
                );

                Files.writeString(pomPath, updated, StandardCharsets.UTF_8);
                System.out.println("✔ Added SLF4J dependency to " + pomPath);

            } else if (content.contains("</project>")) {

                // No <dependencies> section at all — create one
                String deps =
                        "    <dependencies>\n" +
                        slf4jDep +
                        "    </dependencies>\n";

                String updated = content.replace(
                        "</project>",
                        deps + "</project>"
                );

                Files.writeString(pomPath, updated, StandardCharsets.UTF_8);
                System.out.println("✔ Created <dependencies> block with SLF4J in " + pomPath);
            }

        } catch (IOException e) {
            System.err.println("Failed to update pom.xml with SLF4J dependency: " + e.getMessage());
        }
    }
}