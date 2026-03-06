package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class ReplaceGenericExceptionStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REPLACE_GENERIC_EXCEPTION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        boolean modified = false;

        // 1. Handle throw new Exception(...) / throw new RuntimeException(...)
        for (Object node : cu.findAll(com.github.javaparser.ast.stmt.ThrowStmt.class)) {
            com.github.javaparser.ast.stmt.ThrowStmt throwStmt = (com.github.javaparser.ast.stmt.ThrowStmt) node;
            if (throwStmt.getBegin().isPresent() && throwStmt.getBegin().get().line == line) {
                if (throwStmt.getExpression() instanceof com.github.javaparser.ast.expr.ObjectCreationExpr) {
                    com.github.javaparser.ast.expr.ObjectCreationExpr oce = (com.github.javaparser.ast.expr.ObjectCreationExpr) throwStmt
                            .getExpression();
                    String typeName = oce.getType().asString();
                    if (typeName.equals("Exception") || typeName.equals("RuntimeException")) {
                        // Change to a slightly more specific unchecked exception if possible
                        // or just stick to RuntimeException if it was Exception
                        oce.setType("IllegalArgumentException");
                        modified = true;
                    }
                }
            }
        }

        // 2. Handle throws Exception in method signatures
        for (com.github.javaparser.ast.body.MethodDeclaration md : cu
                .findAll(com.github.javaparser.ast.body.MethodDeclaration.class)) {
            if (md.getBegin().isPresent() && md.getBegin().get().line == line) {
                java.util.List<com.github.javaparser.ast.type.ReferenceType> toRemove = new java.util.ArrayList<>();
                for (com.github.javaparser.ast.type.ReferenceType thrown : md.getThrownExceptions()) {
                    String name = thrown.asString();
                    if (name.equals("Exception") || name.equals("RuntimeException") || name.equals("Throwable")) {
                        toRemove.add(thrown);
                        modified = true;
                    }
                }
                for (com.github.javaparser.ast.type.ReferenceType thrown : toRemove) {
                    md.getThrownExceptions().remove(thrown);
                }
            }
        }

        return modified;
    }
}