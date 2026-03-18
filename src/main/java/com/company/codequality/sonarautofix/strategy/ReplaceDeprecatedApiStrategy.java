package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ReplaceDeprecatedApiStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REPLACE_DEPRECATED_API;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;
        boolean needsLocalDate = false;
        boolean needsLocalTime = false;

        List<MethodCallExpr> calls = cu.findAll(MethodCallExpr.class);

        for (MethodCallExpr call : calls) {

            String method = call.getNameAsString();

            if (!isDeprecatedDateMethod(method)) {
                continue;
            }

            MethodCallExpr replacement = buildReplacement(method);

            if (replacement == null) {
                continue;
            }

            call.replace(replacement);
            fixed = true;

            if (isDateMethod(method)) {
                needsLocalDate = true;
            }

            if (isTimeMethod(method)) {
                needsLocalTime = true;
            }

            removeUnusedDateVariables(cu);
        }

        if (!fixed) {
            return false;
        }

        if (needsLocalDate) {
            cu.addImport("java.time.LocalDate");
        }

        if (needsLocalTime) {
            cu.addImport("java.time.LocalTime");
        }

        removeUnusedDateImport(cu);

        return true;
    }

    private boolean isDeprecatedDateMethod(String name) {

        return name.equals("getYear")
                || name.equals("getMonth")
                || name.equals("getDay")
                || name.equals("getHours")
                || name.equals("getMinutes")
                || name.equals("getSeconds");
    }

    private boolean isDateMethod(String name) {
        return name.equals("getYear")
                || name.equals("getMonth")
                || name.equals("getDay");
    }

    private boolean isTimeMethod(String name) {
        return name.equals("getHours")
                || name.equals("getMinutes")
                || name.equals("getSeconds");
    }

    private MethodCallExpr buildReplacement(String method) {

        switch (method) {

            case "getYear":
                return new MethodCallExpr(
                        new MethodCallExpr(new NameExpr("LocalDate"), "now"),
                        "getYear"
                );

            case "getMonth":
                return new MethodCallExpr(
                        new MethodCallExpr(new NameExpr("LocalDate"), "now"),
                        "getMonthValue"
                );

            case "getDay":
                return new MethodCallExpr(
                        new MethodCallExpr(new NameExpr("LocalDate"), "now"),
                        "getDayOfWeek"
                );

            case "getHours":
                return new MethodCallExpr(
                        new MethodCallExpr(new NameExpr("LocalTime"), "now"),
                        "getHour"
                );

            case "getMinutes":
                return new MethodCallExpr(
                        new MethodCallExpr(new NameExpr("LocalTime"), "now"),
                        "getMinute"
                );

            case "getSeconds":
                return new MethodCallExpr(
                        new MethodCallExpr(new NameExpr("LocalTime"), "now"),
                        "getSecond"
                );
        }

        return null;
    }

    private void removeUnusedDateVariables(CompilationUnit cu) {

        List<VariableDeclarator> vars = cu.findAll(VariableDeclarator.class);

        for (VariableDeclarator var : vars) {

            if (!var.getType().asString().equals("Date")) {
                continue;
            }

            String name = var.getNameAsString();

            long usages = cu.findAll(NameExpr.class)
                    .stream()
                    .filter(n -> n.getNameAsString().equals(name))
                    .count();

            if (usages > 1) {
                continue;
            }

            var.findAncestor(ExpressionStmt.class)
                    .ifPresent(ExpressionStmt::remove);
        }
    }

    private void removeUnusedDateImport(CompilationUnit cu) {

        boolean stillUsed = cu.findAll(ClassOrInterfaceType.class)
                .stream()
                .anyMatch(t -> t.getNameAsString().equals("Date"));

        if (!stillUsed) {
            cu.getImports().removeIf(i ->
                    i.getNameAsString().equals("java.util.Date"));
        }
    }
}