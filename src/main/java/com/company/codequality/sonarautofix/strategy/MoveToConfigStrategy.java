package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class MoveToConfigStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.MOVE_TO_CONFIG;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        // First try to find a FieldDeclaration that contains the line
        for (com.github.javaparser.ast.body.FieldDeclaration field : cu
                .findAll(com.github.javaparser.ast.body.FieldDeclaration.class)) {
            if (field.getRange().isPresent() && field.getRange().get().begin.line <= line
                    && field.getRange().get().end.line >= line) {
                for (com.github.javaparser.ast.body.VariableDeclarator var : field.getVariables()) {
                    if (var.getInitializer().isPresent() && var.getInitializer().get().isStringLiteralExpr()) {
                        return processLiteral(cu, var.getInitializer().get().asStringLiteralExpr(), field);
                    }
                }
            }
        }

        // Then try to find a StringLiteralExpr directly
        for (com.github.javaparser.ast.expr.StringLiteralExpr literal : cu
                .findAll(com.github.javaparser.ast.expr.StringLiteralExpr.class)) {
            if (literal.getRange().isPresent()) {
                var range = literal.getRange().get();
                if (range.begin.line <= line && range.end.line >= line) {
                    com.github.javaparser.ast.body.FieldDeclaration field = (com.github.javaparser.ast.body.FieldDeclaration) literal
                            .findAncestor(com.github.javaparser.ast.body.FieldDeclaration.class).orElse(null);
                    return processLiteral(cu, literal, field);
                }
            }
        }

        return false;
    }

    private boolean processLiteral(CompilationUnit cu, com.github.javaparser.ast.expr.StringLiteralExpr literal,
            com.github.javaparser.ast.body.FieldDeclaration existingField) {
        String value = literal.getValue();
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz = (com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) literal
                .findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).orElse(null);

        if (clazz == null)
            return false;

        String varName = generateConfigVarName(value, existingField);
        String configKey = varName.toLowerCase().replace("_", ".");

        if (existingField != null) {
            existingField.removeModifier(com.github.javaparser.ast.Modifier.Keyword.STATIC);
            existingField.removeModifier(com.github.javaparser.ast.Modifier.Keyword.FINAL);
            existingField.addSingleMemberAnnotation("Value",
                    new com.github.javaparser.ast.expr.StringLiteralExpr("${" + configKey + ":" + value + "}"));
            existingField.getVariable(0).removeInitializer();
        } else {
            com.github.javaparser.ast.body.FieldDeclaration newField = clazz.addField("String", varName,
                    com.github.javaparser.ast.Modifier.Keyword.PRIVATE);
            newField.addSingleMemberAnnotation("Value",
                    new com.github.javaparser.ast.expr.StringLiteralExpr("${" + configKey + ":" + value + "}"));
            literal.replace(new com.github.javaparser.ast.expr.NameExpr(varName));
        }

        cu.addImport("org.springframework.beans.factory.annotation.Value");
        return true;
    }

    private boolean isSensitiveValue(String s) {
        return s.startsWith("http://") || s.startsWith("https://") ||
                s.startsWith("/") || s.contains(":\\") ||
                s.matches("^\\d{1,3}(\\.\\d{1,3}){3}.*");
    }

    private String generateConfigVarName(String val, com.github.javaparser.ast.body.FieldDeclaration field) {
        if (field != null)
            return field.getVariable(0).getNameAsString();
        String cleaned = val.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        if (cleaned.length() > 20)
            cleaned = cleaned.substring(0, 20);
        return "CONFIG_" + cleaned;
    }
}