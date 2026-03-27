package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import org.springframework.stereotype.Component;
import java.util.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Optional;

@Component
public class ReplaceGenericExceptionStrategy implements FixStrategy {

    /**
     * Safe JDK replacements — always available, no import injection needed
     * for unchecked ones (RuntimeException subclasses).
     */
    private static final Map<String, String> JDK_FALLBACK_MAP = Map.of(
        "Exception",        "IOException",        // checked → checked
        "RuntimeException", "IllegalStateException", // unchecked → unchecked
        "Throwable",        "Exception"           // Throwable → Exception (step down)
    );

    /**
     * JDK exceptions that need an explicit import added.
     */
    private static final Map<String, String> IMPORT_MAP = Map.of(
        "IOException", "java.io.IOException"
    );
    // IllegalStateException and Exception are in java.lang — no import needed.

    @Override
    public FixType getFixType() {
        return FixType.REPLACE_GENERIC_EXCEPTION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        boolean fixed = false;

        // Detect the target project's base package from the CU itself
        String targetPackage = cu.getPackageDeclaration()
                .map(pd -> rootPackage(pd.getNameAsString()))
                .orElse(null);

        // 1. Replace: throw new Exception(...)
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            if (!shouldProcess(expr, line)) continue;
            String type = expr.getType().asString();
            if (isGeneric(type)) {
                String replacement = resolveReplacement(type, targetPackage, cu);
                expr.setType(replacement);
                addImportIfNeeded(cu, replacement, targetPackage);
                fixed = true;
            }
        }

        // 2. Replace: throws Exception in method signatures
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (!shouldProcess(method, line)) continue;
            for (ReferenceType thrown : new ArrayList<>(method.getThrownExceptions())) {
                String name = thrown.asString();
                if (isGeneric(name)) {
                    String replacement = resolveReplacement(name, targetPackage, cu);
                    thrown.replace(new ClassOrInterfaceType(null, replacement));
                    addImportIfNeeded(cu, replacement, targetPackage);
                    fixed = true;
                }
            }
        }

        return fixed;
    }

    /**
     * Resolution order:
     * 1. Find an existing custom exception in the target project's own source tree
     * 2. Fall back to a safe JDK built-in
     */
    private String resolveReplacement(String genericType,
                                      String targetPackage,
                                      CompilationUnit cu) {
        if (targetPackage != null) {
            Optional<String> custom = findCustomException(targetPackage, cu);
            if (custom.isPresent())
                return custom.get();
        }
        return JDK_FALLBACK_MAP.getOrDefault(genericType, "IllegalStateException");
    }

    /**
     * Scans the target project's source tree for any class ending in "Exception"
     * that lives under its base package directory.
     *
     * Works by inspecting the source root derived from the CU's storage path.
     */
    private Optional<String> findCustomException(String basePackage,
                                                  CompilationUnit cu) {
        try {
            // Derive source root from CU file path
            Path filePath = cu.getStorage()
                    .map(s -> s.getPath())
                    .orElse(null);

            if (filePath == null) return Optional.empty();

            // Walk up to src/main/java
            Path sourceRoot = findSourceRoot(filePath);
            if (sourceRoot == null) return Optional.empty();

            Path packageRoot = sourceRoot.resolve(
                basePackage.replace('.', '/')
            );

            if (!Files.exists(packageRoot)) return Optional.empty();

            // Find the first *Exception.java in the project
            try (var stream = Files.walk(packageRoot)) {
                return stream
                    .filter(p -> p.getFileName().toString().endsWith("Exception.java"))
                    .map(p -> toClassName(p, sourceRoot))
                    .filter(name -> name != null && !name.isEmpty())
                    .map(fqn -> fqn.substring(fqn.lastIndexOf('.') + 1)) // simple name
                    .findFirst();
            }

        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Path findSourceRoot(Path filePath) {
        Path current = filePath.getParent();
        while (current != null) {
            if (current.endsWith(Paths.get("src", "main", "java")) ||
                current.endsWith(Paths.get("src", "test", "java"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private String toClassName(Path javaFile, Path sourceRoot) {
        try {
            Path relative = sourceRoot.relativize(javaFile);
            String path = relative.toString()
                    .replace(java.io.File.separatorChar, '.')
                    .replace('/', '.');
            // strip .java suffix
            if (path.endsWith(".java"))
                path = path.substring(0, path.length() - 5);
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Only adds an import if:
     * - The replacement is a JDK type that needs one (e.g. IOException), OR
     * - The replacement is a custom type from a DIFFERENT package than the CU
     */
    private void addImportIfNeeded(CompilationUnit cu,
                                   String simpleName,
                                   String targetPackage) {
        // java.lang types (Exception, IllegalStateException etc.) — no import needed
        if (isJavaLang(simpleName)) return;

        // JDK types with known FQNs
        if (IMPORT_MAP.containsKey(simpleName)) {
            addImport(cu, IMPORT_MAP.get(simpleName));
            return;
        }

        // Custom exception — only import if it's not in the same package
        if (targetPackage != null) {
            String cuPackage = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            // Find the actual FQN by scanning source tree
            try {
                Path filePath = cu.getStorage()
                        .map(s -> s.getPath()).orElse(null);
                if (filePath == null) return;

                Path sourceRoot = findSourceRoot(filePath);
                if (sourceRoot == null) return;

                try (var stream = Files.walk(
                        sourceRoot.resolve(targetPackage.replace('.', '/')))) {
                    stream.filter(p -> p.getFileName()
                                        .toString()
                                        .equals(simpleName + ".java"))
                          .map(p -> toClassName(p, sourceRoot))
                          .filter(fqn -> fqn != null)
                          .findFirst()
                          .ifPresent(fqn -> {
                              String pkg = fqn.contains(".")
                                  ? fqn.substring(0, fqn.lastIndexOf('.'))
                                  : "";
                              if (!pkg.equals(cuPackage))
                                  addImport(cu, fqn);
                          });
                }
            } catch (IOException ignored) {}
        }
    }

    private void addImport(CompilationUnit cu, String fqn) {
        boolean exists = cu.getImports().stream()
                .anyMatch(i -> i.getNameAsString().equals(fqn));
        if (!exists)
            cu.addImport(fqn);
    }

    private boolean isJavaLang(String simpleName) {
        return Set.of("Exception", "RuntimeException", "IllegalStateException",
                      "IllegalArgumentException", "UnsupportedOperationException",
                      "NullPointerException", "Error", "Throwable")
                  .contains(simpleName);
    }

    private boolean isGeneric(String type) {
        return JDK_FALLBACK_MAP.containsKey(type);
    }

    private String rootPackage(String pkg) {
        // com.bank.modernize.service → com.bank.modernize
        String[] parts = pkg.split("\\.");
        if (parts.length <= 2) return pkg;
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    private boolean shouldProcess(Node node, int line) {
        if (line == -1) return true;
        return node.getBegin()
                .map(p -> Math.abs(p.line - line) <= 2)
                .orElse(false);
    }
}