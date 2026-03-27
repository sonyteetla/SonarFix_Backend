package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ExtractConstantStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.EXTRACT_CONSTANT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (LiteralExpr literal : cu.findAll(LiteralExpr.class)) {

            if (literal.getBegin().isEmpty())
                continue;

            if (literal.getBegin().get().line != line)
                continue;

            if (shouldIgnoreLiteral(literal))
                continue;

            if (literal.findAncestor(FieldDeclaration.class).isPresent())
                continue;

            Optional<ClassOrInterfaceDeclaration> clazzOpt =
                    literal.findAncestor(ClassOrInterfaceDeclaration.class);

            if (clazzOpt.isEmpty())
                continue;

            ClassOrInterfaceDeclaration clazz = clazzOpt.get();

            String value = literal.toString();
            String constantName = generateConstantName(literal);

            if (!constantExists(clazz, constantName)) {

                String type = resolveType(literal);

                FieldDeclaration field =
                        StaticJavaParser.parseBodyDeclaration(
                                "private static final " + type + " "
                                        + constantName + " = " + value + ";"
                        ).asFieldDeclaration();

                // Add at a safer place or check if it already shifted
                clazz.getMembers().add(0, field);
            }

            literal.replace(new NameExpr(constantName));

            fixed = true;
        }

        return fixed;
    }

    private boolean shouldIgnoreLiteral(LiteralExpr literal) {

        // Ignore small integers
        if (literal instanceof IntegerLiteralExpr intLit) {
            int v = intLit.asInt();
            return v == 0 || v == 1 || v == -1;
        }

        // Ignore functional string literals
        if (literal instanceof StringLiteralExpr str) {

            String value = str.asString();

            // Skip delimiters and common symbols
            if (value.length() <= 2) return true;

            if (value.matches("^[@.,:;|\\-_/\\\\]$")) return true;

            // Skip regex-like patterns
            if (value.contains("@") || value.contains(".")) return true;

            // Skip empty / whitespace
            if (value.trim().isEmpty()) return true;
        }

        return false;
    }

    private boolean constantExists(ClassOrInterfaceDeclaration clazz,
                                   String name) {

        return clazz.getFields().stream()
                .flatMap(f -> f.getVariables().stream())
                .anyMatch(v -> v.getNameAsString().equals(name));
    }

    private String resolveType(LiteralExpr literal) {

        if (literal instanceof IntegerLiteralExpr)
            return "int";

        if (literal instanceof LongLiteralExpr)
            return "long";

        if (literal instanceof DoubleLiteralExpr)
            return "double";

        if (literal instanceof StringLiteralExpr)
            return "String";

        return "Object";
    }

    private String generateConstantName(LiteralExpr literal) {

        String base;

        if (literal instanceof StringLiteralExpr str) {

            base = str.asString()
                    .replaceAll("[^a-zA-Z0-9]", "_")
                    .toUpperCase();

            if (base.length() > 20)
                base = base.substring(0, 20);

        } else {

            base = literal.toString()
                    .replace("-", "NEG_")
                    .replace(".", "_");
        }

        if (Character.isDigit(base.charAt(0))) {
            base = "_" + base;
        }

        return "CONST_" + base;
    }
}