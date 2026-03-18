package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class MoveToConfigStrategy implements FixStrategy {

    private static final Map<String, String> literalToKey = new HashMap<>();
    private static final Set<String> usedKeys = new HashSet<>();

    private static final Pattern URL_PATTERN =
            Pattern.compile("^(http|https)://.*");

    private static final Pattern PATH_PATTERN =
            Pattern.compile("^[A-Za-z]:\\\\.*|^/.*");

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[A-Za-z0-9-_]{24,}");

    private static final Pattern IP_PATTERN =
            Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+.*");

    @Override
    public FixType getFixType() {
        return FixType.MOVE_TO_CONFIG;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        Optional<TypeDeclaration<?>> typeOpt = cu.getPrimaryType();
        if (typeOpt.isEmpty())
            return false;

        TypeDeclaration<?> type = typeOpt.get();

        for (StringLiteralExpr literal : cu.findAll(StringLiteralExpr.class)) {

            if (!shouldProcess(literal, line))
                continue;

            if (literal.findAncestor(AnnotationExpr.class).isPresent())
                continue;

            String value = literal.getValue();

            if (!isConfigCandidate(value))
                continue;

            String key = generateKey(value);
            String fieldName = toFieldName(key);

            literal.replace(new NameExpr(fieldName));

            if (!fieldExists(type, fieldName)) {

                FieldDeclaration field = new FieldDeclaration();
                field.addModifier(Modifier.Keyword.PRIVATE);

                VariableDeclarator var =
                        new VariableDeclarator(
                                new ClassOrInterfaceType(null, "String"),
                                fieldName
                        );

                field.addVariable(var);

                field.addAnnotation(
                        new SingleMemberAnnotationExpr(
                                new Name("Value"),
                                new StringLiteralExpr("${" + key + "}")
                        )
                );

                insertField(type, field);
            }

            addImportIfMissing(cu);

            appendToProperties(key, value);

            fixed = true;
        }

        return fixed;
    }

    private boolean shouldProcess(StringLiteralExpr literal, int line) {

        if (line == -1)
            return true;

        return literal.getBegin()
                .map(p -> Math.abs(p.line - line) <= 1)
                .orElse(false);
    }

    private boolean isConfigCandidate(String value) {

        return URL_PATTERN.matcher(value).matches()
                || PATH_PATTERN.matcher(value).matches()
                || TOKEN_PATTERN.matcher(value).matches()
                || IP_PATTERN.matcher(value).matches();
    }

    private String generateKey(String value) {

        if (literalToKey.containsKey(value))
            return literalToKey.get(value);

        String base;

        if (value.startsWith("jdbc"))
            base = "database.url";
        else if (value.startsWith("http"))
            base = "api.endpoint";
        else if (value.startsWith("/") || value.contains("\\"))
            base = "file.path";
        else if (value.matches("\\d+\\.\\d+\\.\\d+\\.\\d+.*"))
            base = "server.ip";
        else if (value.length() > 24)
            base = "security.token";
        else
            base = "external.config";

        String key = base;
        int i = 1;

        while (usedKeys.contains(key)) {
            key = base + "." + (i++);
        }

        usedKeys.add(key);
        literalToKey.put(value, key);

        return key;
    }

    private String toFieldName(String key) {

        String[] parts = key.split("\\.");

        StringBuilder sb = new StringBuilder(parts[0]);

        for (int i = 1; i < parts.length; i++) {

            sb.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1));
        }

        return sb.toString();
    }

    private boolean fieldExists(TypeDeclaration<?> type, String fieldName) {

        return type.getFields().stream()
                .flatMap(f -> f.getVariables().stream())
                .anyMatch(v -> v.getNameAsString().equals(fieldName));
    }

    private void insertField(TypeDeclaration<?> type, FieldDeclaration field) {

        NodeList<BodyDeclaration<?>> members = type.getMembers();

        int index = 0;

        for (int i = 0; i < members.size(); i++) {

            if (members.get(i) instanceof FieldDeclaration)
                index = i + 1;
        }

        members.add(index, field);
    }

    private void addImportIfMissing(CompilationUnit cu) {

        boolean exists = cu.getImports().stream()
                .anyMatch(i -> i.getNameAsString()
                        .equals("org.springframework.beans.factory.annotation.Value"));

        if (!exists)
            cu.addImport("org.springframework.beans.factory.annotation.Value");
    }

    private void appendToProperties(String key, String value) {

        try {

            Path properties =
                    Paths.get("src", "main", "resources", "application.properties");

            if (!Files.exists(properties)) {

                Files.createDirectories(properties.getParent());
                Files.createFile(properties);
            }

            List<String> lines = Files.readAllLines(properties);

            for (String line : lines) {
                if (line.startsWith(key + "="))
                    return;
            }

            Files.write(
                    properties,
                    (key + "=" + value + System.lineSeparator())
                            .getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND
            );

        } catch (Exception ignored) {}
    }
}