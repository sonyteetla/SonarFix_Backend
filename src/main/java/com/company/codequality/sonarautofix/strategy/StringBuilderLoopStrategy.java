package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import org.springframework.stereotype.Component;

@Component
public class StringBuilderLoopStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.STRING_BUILDER_LOOP;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (AssignExpr assign : cu.findAll(AssignExpr.class)) {

            if (!assign.getOperator().equals(AssignExpr.Operator.PLUS)) continue;

            if (!(assign.getValue() instanceof BinaryExpr)) continue;

            // Check if inside loop
            boolean insideLoop =
                    assign.findAncestor(ForStmt.class).isPresent() ||
                    assign.findAncestor(WhileStmt.class).isPresent();

            if (!insideLoop) continue;

            String varName = assign.getTarget().toString();

            // Replace "str += x" → "sb.append(x)"
            MethodCallExpr append =
                    new MethodCallExpr("sb.append",
                            ((BinaryExpr) assign.getValue()).getRight());

            assign.replace(append);
            fixed = true;
        }

        return fixed;
    }
}