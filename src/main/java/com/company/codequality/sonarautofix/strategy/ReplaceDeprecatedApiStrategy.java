package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class ReplaceDeprecatedApiStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REPLACE_DEPRECATED_API;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        for (com.github.javaparser.ast.expr.ObjectCreationExpr oce : cu
                .findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class)) {

            if (literalMatch(oce, line)) {
                String typeName = oce.getType().asString();
                if (typeName.equals("Date") || typeName.equals("java.util.Date")) {

                    if (oce.getArguments().isEmpty()) {
                        oce.replace(new com.github.javaparser.ast.expr.MethodCallExpr(
                                new com.github.javaparser.ast.expr.NameExpr("Instant"),
                                "now"));

                        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals("java.time.Instant"))) {
                            cu.addImport("java.time.Instant");
                        }
                    } else if (oce.getArguments().size() >= 3) {
                        // For new Date(y, m, d), LocalDate.of is a decent replacement.
                        com.github.javaparser.ast.expr.MethodCallExpr mce = new com.github.javaparser.ast.expr.MethodCallExpr(
                                new com.github.javaparser.ast.expr.NameExpr("LocalDate"),
                                "of");
                        for (com.github.javaparser.ast.expr.Expression arg : oce.getArguments()) {
                            mce.addArgument(arg.clone());
                        }
                        oce.replace(mce);

                        if (cu.getImports().stream()
                                .noneMatch(i -> i.getNameAsString().equals("java.time.LocalDate"))) {
                            cu.addImport("java.time.LocalDate");
                        }
                    } else {
                        continue;
                    }

                    // Try to remove java.util.Date import if it's no longer used
                    cu.getImports().removeIf(i -> i.getNameAsString().equals("java.util.Date"));
                    return true;
                }
            }
        }
        return false;
    }

    private boolean literalMatch(com.github.javaparser.ast.Node node, int line) {
        return node.getRange().map(range -> {
            return (range.begin.line <= line && range.end.line >= line)
                    || (Math.abs(range.begin.line - line) <= 1);
        }).orElse(false);
    }
}