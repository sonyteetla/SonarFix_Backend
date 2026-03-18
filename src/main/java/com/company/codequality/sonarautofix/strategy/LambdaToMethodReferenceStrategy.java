package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.company.codequality.sonarautofix.util.LoggerUtil;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;

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

                if (lambda.getBegin().isEmpty()
                        || lambda.getBegin().get().line != line)
                    continue;

                if (lambda.getParameters().size() != 1)
                    continue;

                String paramName =
                        lambda.getParameter(0).getNameAsString();

                Expression bodyExpr = null;

             // Case 1: x -> System.out.println(x)
             if (lambda.getBody() instanceof ExpressionStmt exprStmt) {
                 bodyExpr = exprStmt.getExpression();
             }

             // Case 2: x -> { System.out.println(x); }
             else if (lambda.getBody() instanceof com.github.javaparser.ast.stmt.BlockStmt block) {

                 if (block.getStatements().size() != 1)
                     continue;

                 var stmt = block.getStatement(0);

                 if (!(stmt instanceof com.github.javaparser.ast.stmt.ExpressionStmt exprStmt))
                     continue;

                 bodyExpr = exprStmt.getExpression();
             }
             
                if (!(bodyExpr instanceof MethodCallExpr methodCall))
                    continue;

                if (methodCall.getArguments().size() != 1)
                    continue;

                Expression arg = methodCall.getArgument(0);

                if (!(arg instanceof NameExpr nameExpr)
                        || !nameExpr.getNameAsString().equals(paramName))
                    continue;

                if (methodCall.getScope().isEmpty())
                    continue;

                Expression scope = methodCall.getScope().get();
                String methodName = methodCall.getNameAsString();

                // Handle System.out.println → log.info
                if (scope instanceof FieldAccessExpr
                        && scope.toString().equals("System.out")
                        && methodName.equals("println")) {

                    LoggerUtil.ensureSlf4jLoggerExists(cu);

                    scope = new NameExpr("log");
                    methodName = "info";
                }

                MethodReferenceExpr ref =
                        new MethodReferenceExpr(
                                scope,
                                new NodeList<>(),
                                methodName
                        );

                lambda.replace(ref);

                fixed = true;

            } catch (Exception ignored) {}
        }

        return fixed;
    }
}