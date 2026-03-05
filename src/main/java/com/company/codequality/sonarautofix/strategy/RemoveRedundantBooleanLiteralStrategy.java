package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.types.ResolvedType;
import org.springframework.stereotype.Component;

@Component
public class RemoveRedundantBooleanLiteralStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_REDUNDANT_BOOLEAN;
    }

    @Override
    public boolean apply(CompilationUnit cu, int startLine) {

        for (BinaryExpr binary : cu.findAll(BinaryExpr.class)) {

            if (!binary.getRange().isPresent()) continue;

            var range = binary.getRange().get();
            if (range.begin.line > startLine || range.end.line < startLine)
                continue;

            if (binary.getOperator() != BinaryExpr.Operator.EQUALS &&
                binary.getOperator() != BinaryExpr.Operator.NOT_EQUALS)
                continue;

            Expression left = binary.getLeft();
            Expression right = binary.getRight();

            if (left instanceof BooleanLiteralExpr literal) {
                return transform(binary, right, literal.getValue());
            }

            if (right instanceof BooleanLiteralExpr literal) {
                return transform(binary, left, literal.getValue());
            }
        }

        return false;
    }

    private boolean transform(BinaryExpr binary,
                              Expression expr,
                              boolean literalValue) {

        // Ensure expr is primitive boolean
        if (!isPrimitiveBoolean(expr))
            return false;

        Expression cleanedExpr = removeRedundantParentheses(expr.clone());

        Expression replacement;

        boolean isEquals = binary.getOperator() == BinaryExpr.Operator.EQUALS;

        if (isEquals) {
            replacement = literalValue
                    ? cleanedExpr
                    : negate(cleanedExpr);
        } else {
            replacement = literalValue
                    ? negate(cleanedExpr)
                    : cleanedExpr;
        }

        binary.replace(replacement);
        return true;
    }

    private boolean isPrimitiveBoolean(Expression expr) {
        try {
            ResolvedType type = expr.calculateResolvedType();
            return type.isPrimitive() &&
                   type.describe().equals("boolean");
        } catch (Exception e) {
            return false;
        }
    }

    private Expression negate(Expression expr) {

        // Avoid !!flag
        if (expr instanceof UnaryExpr unary &&
            unary.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {

            return removeRedundantParentheses(unary.getExpression());
        }

        return new UnaryExpr(
                new EnclosedExpr(expr),
                UnaryExpr.Operator.LOGICAL_COMPLEMENT
        );
    }

    private Expression removeRedundantParentheses(Expression expr) {
        while (expr instanceof EnclosedExpr enclosed) {
            expr = enclosed.getInner();
        }
        return expr;
    }
}