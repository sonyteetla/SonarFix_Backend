package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import org.springframework.stereotype.Component;

@Component
public class ReplaceDeprecatedApiStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REPLACE_DEPRECATED_API;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (MethodCallExpr method : cu.findAll(MethodCallExpr.class)) {

            // detect getYear() calls
            if (!method.getNameAsString().equals("getYear")) {
                continue;
            }

            // ensure it is called on Date object
            if (!method.getScope().isPresent()) {
                continue;
            }

            // Replace with LocalDate.now().getYear()
            MethodCallExpr newCall =
                    new MethodCallExpr(
                            new MethodCallExpr(
                                    new NameExpr("LocalDate"),
                                    "now"
                            ),
                            "getYear"
                    );

            method.replace(newCall);
            fixed = true;
        }

        if (fixed) {
            cu.addImport("java.time.LocalDate");
        }

        return fixed;
    }
}