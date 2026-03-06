package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import org.springframework.stereotype.Component;

@Component
public class UseEqualsForStringStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.USE_EQUALS_FOR_STRING;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (BinaryExpr expr : cu.findAll(BinaryExpr.class)) {

            // Respect line filter
            if (line != -1 && expr.getBegin().isPresent()
                    && expr.getBegin().get().line != line) {
                continue;
            }

            // Only process ==
            if (expr.getOperator() != BinaryExpr.Operator.EQUALS) {
                continue;
            }

            Expression left = expr.getLeft();
            Expression right = expr.getRight();

            // Only convert when one side is a string literal
            if (!(left instanceof StringLiteralExpr)
                    && !(right instanceof StringLiteralExpr)) {
                continue;
            }

            MethodCallExpr equalsCall;

            // Prefer literal.equals(variable) to avoid NPE
            if (left instanceof StringLiteralExpr) {

                equalsCall = new MethodCallExpr(left, "equals");
                equalsCall.addArgument(right);

            } else {

                equalsCall = new MethodCallExpr(right, "equals");
                equalsCall.addArgument(left);
            }

            expr.replace(equalsCall);

            fixed = true;
        }

        return fixed;
    }
}