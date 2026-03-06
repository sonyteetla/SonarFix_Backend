package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import org.springframework.stereotype.Component;

@Component
public class LambdaToMethodReferenceStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.LAMBDA_TO_METHOD_REFERENCE;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (LambdaExpr lambda : cu.findAll(LambdaExpr.class)) {

            try {

                if (lambda.getParameters().size() != 1)
                    continue;

                if (!lambda.getBody().isExpressionStmt())
                    continue;

                Expression expr =
                        lambda.getBody().asExpressionStmt().getExpression();

                if (!(expr instanceof MethodCallExpr methodCall))
                    continue;

                if (methodCall.getArguments().size() != 1)
                    continue;

                String param =
                        lambda.getParameter(0).getNameAsString();

                if (!methodCall.getArgument(0).toString().equals(param))
                    continue;

                if (methodCall.getScope().isEmpty())
                    continue;

                MethodReferenceExpr ref =
                        new MethodReferenceExpr(
                                methodCall.getScope().get(),
                                new NodeList<>(),
                                methodCall.getNameAsString()
                        );

                lambda.replace(ref);

                fixed = true;

            } catch (Exception ignored) {}

        }

        return fixed;
    }
}