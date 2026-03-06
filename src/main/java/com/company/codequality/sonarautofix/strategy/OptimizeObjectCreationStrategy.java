package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.springframework.stereotype.Component;

@Component
public class OptimizeObjectCreationStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.OPTIMIZE_OBJECT_CREATION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        boolean modified = false;

        for (ObjectCreationExpr oce : cu.findAll(ObjectCreationExpr.class)) {
            if (oce.getBegin().isPresent() && oce.getBegin().get().line == line) {
                String typeName = oce.getType().asString();
                if (typeName.equals("String") && oce.getArguments().size() == 1) {
                    oce.replace(oce.getArgument(0).clone());
                    modified = true;
                } else if (typeName.equals("Boolean") && oce.getArguments().size() == 1) {
                    // Boolean b = new Boolean(true) -> Boolean b = Boolean.valueOf(true)
                    oce.replace(new com.github.javaparser.ast.expr.MethodCallExpr(
                            new com.github.javaparser.ast.expr.NameExpr("Boolean"),
                            "valueOf",
                            oce.getArguments()));
                    modified = true;
                }
            }
        }

        return modified;
    }
}