package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import org.springframework.stereotype.Component;

/**
 * java:S1698 — Replace == / != with .equals() for String comparisons.
 *
 * Before: if (str1 == str2)
 * After: if (str1.equals(str2))
 *
 * Before: if (str1 != str2)
 * After: if (!str1.equals(str2))
 */
@Component
public class UseEqualsForStringStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.USE_EQUALS_FOR_STRING;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        for (BinaryExpr binary : cu.findAll(BinaryExpr.class)) {
            if (literalMatch(binary, line)) {
                BinaryExpr.Operator op = binary.getOperator();
                if (op != BinaryExpr.Operator.EQUALS && op != BinaryExpr.Operator.NOT_EQUALS)
                    continue;

                Expression left = binary.getLeft();
                Expression right = binary.getRight();

                Expression target;
                Expression arg;

                if (left instanceof StringLiteralExpr) {
                    target = left;
                    arg = right;
                } else if (right instanceof StringLiteralExpr) {
                    target = right;
                    arg = left;
                } else {
                    target = left;
                    arg = right;
                }

                MethodCallExpr equalsCall = new MethodCallExpr(target.clone(), "equals");
                equalsCall.addArgument(arg.clone());

                Expression replacement = (op == BinaryExpr.Operator.NOT_EQUALS)
                        ? new UnaryExpr(new EnclosedExpr(equalsCall), UnaryExpr.Operator.LOGICAL_COMPLEMENT)
                        : equalsCall;

                binary.replace(replacement);
                return true;
            }
        }
        return false;
    }

    private boolean literalMatch(BinaryExpr binary, int line) {
        return binary.getRange().map(range -> {
            return (range.begin.line <= line && range.end.line >= line)
                    || (Math.abs(range.begin.line - line) <= 1);
        }).orElse(false);
    }
}
