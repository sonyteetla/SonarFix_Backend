package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
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

            boolean insideLoop =
                    obj.findAncestor(ForStmt.class).isPresent() ||
                    obj.findAncestor(WhileStmt.class).isPresent();

            if (!insideLoop) continue;

            obj.setComment("TODO: Move object creation outside loop");
            fixed = true;
        }

        return fixed;
    }
}