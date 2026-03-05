package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.springframework.stereotype.Component;

@Component
public class MoveToConfigStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.MOVE_TO_CONFIG;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (StringLiteralExpr literal : cu.findAll(StringLiteralExpr.class)) {

            String value = literal.getValue();

            if (value.startsWith("${")) continue;

            if (value.matches("https?://.*") || value.contains("localhost")) {

                literal.setString("${app.config.url}");
                fixed = true;
            }
        }

        return fixed;
    }
}