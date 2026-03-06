package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class ExtractConstantStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.EXTRACT_CONSTANT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        boolean modified = false;

        for (com.github.javaparser.ast.expr.LiteralExpr literal : cu
                .findAll(com.github.javaparser.ast.expr.LiteralExpr.class)) {
            if (literal.getBegin().isPresent() && literal.getBegin().get().line == line) {

                String value = literal.toString();
                String constantName = "CONSTANT_" + value.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();

                // Add constant to the class
                if (cu.getPrimaryType().isPresent()) {
                    com.github.javaparser.ast.body.TypeDeclaration<?> type = cu.getPrimaryType().get();

                    // Check if constant already exists
                    boolean exists = type.getFields().stream()
                            .anyMatch(f -> f.getVariables().stream()
                                    .anyMatch(v -> v.getNameAsString().equals(constantName)));

                    if (!exists) {
                        String fieldType = "Object";
                        if (literal instanceof com.github.javaparser.ast.expr.IntegerLiteralExpr)
                            fieldType = "int";
                        else if (literal instanceof com.github.javaparser.ast.expr.DoubleLiteralExpr)
                            fieldType = "double";
                        else if (literal instanceof com.github.javaparser.ast.expr.LongLiteralExpr)
                            fieldType = "long";
                        else if (literal instanceof com.github.javaparser.ast.expr.StringLiteralExpr)
                            fieldType = "String";
                        else if (literal instanceof com.github.javaparser.ast.expr.BooleanLiteralExpr)
                            fieldType = "boolean";

                        type.addFieldWithInitializer(fieldType, constantName,
                                (com.github.javaparser.ast.expr.Expression) literal.clone(),
                                com.github.javaparser.ast.Modifier.Keyword.PRIVATE,
                                com.github.javaparser.ast.Modifier.Keyword.STATIC,
                                com.github.javaparser.ast.Modifier.Keyword.FINAL);
                    }

                    // Replace literal with constant name
                    literal.replace(new com.github.javaparser.ast.expr.NameExpr(constantName));
                    modified = true;
                    break;
                }
            }
        }

        return modified;
    }
}