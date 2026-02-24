package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
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

            if (method.getNameAsString().equals("getYear")) {
                method.setName("getYear"); // placeholder (extend mapping later)
                fixed = true;
            }
        }

        return fixed;
    }
}