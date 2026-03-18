package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import org.springframework.stereotype.Component;

@Component
public class ReplaceGenericExceptionStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REPLACE_GENERIC_EXCEPTION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        // 1️⃣ Replace throw new Exception / RuntimeException
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {

            if (!shouldProcess(expr, line))
                continue;

            String type = expr.getType().asString();

            if (isGeneric(type)) {

                expr.setType("IllegalStateException");
                fixed = true;
            }
        }

        // 2️⃣ Replace throws Exception in method signatures
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {

            if (!shouldProcess(method, line))
                continue;

            for (ReferenceType thrown : method.getThrownExceptions()) {

                String name = thrown.asString();

                if (isGeneric(name)) {

                    thrown.replace(
                            new ClassOrInterfaceType(null, "IllegalStateException")
                    );

                    fixed = true;
                }
            }
        }

        return fixed;
    }

    private boolean isGeneric(String type) {

        return "Exception".equals(type) ||
               "RuntimeException".equals(type) ||
               "Throwable".equals(type);
    }

    private boolean shouldProcess(Node node, int line) {

        if (line == -1)
            return true;

        return node.getBegin()
                .map(p -> p.line == line)
                .orElse(false);
    }
}