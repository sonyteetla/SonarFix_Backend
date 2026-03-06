package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Component;

@Component
public class AnonymousToLambdaStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.ANONYMOUS_TO_LAMBDA;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (ObjectCreationExpr obj : cu.findAll(ObjectCreationExpr.class)) {

            if (obj.getAnonymousClassBody().isEmpty())
                continue;

            if (!obj.getType().getNameAsString().equals("Runnable"))
                continue;

            obj.getAnonymousClassBody().get().stream()
                    .filter(m -> m instanceof MethodDeclaration)
                    .map(m -> (MethodDeclaration) m)
                    .filter(m -> m.getNameAsString().equals("run"))
                    .findFirst()
                    .ifPresent(method -> {

                        method.getBody().ifPresent(body -> {

                            LambdaExpr lambda =
                                    new LambdaExpr(
                                            new com.github.javaparser.ast.NodeList<>(),
                                            body
                                    );

                            obj.replace(lambda);
                        });
                    });

            fixed = true;
        }

        return fixed;
    }
}