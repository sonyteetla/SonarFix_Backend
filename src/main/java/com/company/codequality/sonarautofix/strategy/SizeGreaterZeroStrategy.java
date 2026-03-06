package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import org.springframework.stereotype.Component;

@Component
public class SizeGreaterZeroStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.COLLECTION_NOT_EMPTY_CHECK;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (BinaryExpr expr : cu.findAll(BinaryExpr.class)) {

            if (line != -1 && expr.getBegin().isPresent()
                    && expr.getBegin().get().line != line) {
                continue;
            }

            if (expr.getOperator() == BinaryExpr.Operator.GREATER
                    && expr.getRight().toString().equals("0")
                    && expr.getLeft() instanceof MethodCallExpr call
                    && call.getNameAsString().equals("size")
                    && !expr.toString().contains("isEmpty")) {

                call.setName("isEmpty");

                UnaryExpr notEmpty =
                        new UnaryExpr(call, UnaryExpr.Operator.LOGICAL_COMPLEMENT);

                expr.replace(notEmpty);

                fixed = true;
            }
        }

        return fixed;
    }
}