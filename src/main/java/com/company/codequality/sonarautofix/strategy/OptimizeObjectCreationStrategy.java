package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import org.springframework.stereotype.Component;

@Component
public class OptimizeObjectCreationStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.OPTIMIZE_OBJECT_CREATION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (ObjectCreationExpr obj : cu.findAll(ObjectCreationExpr.class)) {

            // Apply only on the requested line unless SmartFix (-1)
            if (line != -1 && obj.getBegin().isPresent()
                    && obj.getBegin().get().line != line) {
                continue;
            }

            // Check if inside loop
            boolean insideLoop =
                    obj.findAncestor(ForStmt.class).isPresent() ||
                    obj.findAncestor(WhileStmt.class).isPresent();

            if (!insideLoop)
                continue;

            try {

                // Check for new String("text")
                if ("String".equals(obj.getType().getNameAsString())
                        && obj.getArguments().size() == 1
                        && obj.getArgument(0) instanceof StringLiteralExpr literal) {

                    // ❗ Skip if used as standalone statement
                    if (obj.getParentNode().isPresent()
                            && obj.getParentNode().get() instanceof ExpressionStmt) {
                        continue;
                    }

                    // Replace with literal
                    obj.replace(new StringLiteralExpr(literal.getValue()));

                    fixed = true;
                }

            } catch (Exception ignored) {}

        }

        return fixed;
    }
}