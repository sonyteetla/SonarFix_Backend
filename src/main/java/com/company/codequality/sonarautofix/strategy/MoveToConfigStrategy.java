package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

@Component
public class MoveToConfigStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.MOVE_TO_CONFIG;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        if (!cu.getPrimaryType().isPresent()) {
            return false;
        }

        var type = cu.getPrimaryType().get();

        for (StringLiteralExpr literal : cu.findAll(StringLiteralExpr.class)) {

            String value = literal.getValue();

            if (!isConfigCandidate(value)) {
                continue;
            }

            String key = generateKey(value);

            // Replace literal with config variable
            literal.replace(new NameExpr(key));

            // Add config field if not already present
            if (!fieldExists(type, key)) {

                VariableDeclarator variable =
                        new VariableDeclarator(
                                new ClassOrInterfaceType(null, "String"),
                                key
                        );

                FieldDeclaration field = new FieldDeclaration();
                field.addVariable(variable);

                NormalAnnotationExpr valueAnnotation = new NormalAnnotationExpr();
                valueAnnotation.setName("Value");
                valueAnnotation.addPair("value", "\"${" + key + "}\"");

                field.addAnnotation(valueAnnotation);

                type.addMember(field);
            }

            cu.addImport("org.springframework.beans.factory.annotation.Value");

            appendToProperties(key, value);

            fixed = true;
        }

        return fixed;
    }

    private boolean isConfigCandidate(String value) {

        return value.startsWith("jdbc:")
                || value.startsWith("http:")
                || value.startsWith("https:")
                || value.contains("://")
                || value.matches("\\d+\\.\\d+\\.\\d+\\.\\d+.*")
                || value.contains("localhost");
    }

    private String generateKey(String value) {

        if (value.startsWith("jdbc")) return "databaseUrl";
        if (value.startsWith("http")) return "apiUrl";
        if (value.contains("localhost")) return "serverUrl";

        return "configValue";
    }

    private boolean fieldExists(com.github.javaparser.ast.body.TypeDeclaration<?> type, String fieldName) {

        return type.getFields().stream()
                .anyMatch(f -> f.getVariables().stream()
                        .anyMatch(v -> v.getNameAsString().equals(fieldName)));
    }

    private void appendToProperties(String key, String value) {

        try {

            Path path = Paths.get("src/main/resources/application.properties");

            String entry = key + "=" + value;

            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
            }

            String content = Files.readString(path);

            if (!content.contains(entry)) {

                Files.write(path,
                        (entry + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                        java.nio.file.StandardOpenOption.APPEND);
            }

        } catch (Exception ignored) {
        }
    }
}