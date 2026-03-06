package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * java:S1604 — Convert single-method anonymous class expressions to lambdas.
 *
 * Before:
 * Runnable r = new Runnable() { public void run() { doSomething(); } };
 * After:
 * Runnable r = () -> doSomething();
 */
@Component
public class AnonymousToLambdaStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.ANONYMOUS_TO_LAMBDA;
    }

    @Override
    public boolean apply(CompilationUnit cu, int startLine) {

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {

            if (!creation.getRange().isPresent())
                continue;
            var range = creation.getRange().get();
            if (range.begin.line > startLine || range.end.line < startLine)
                continue;

            // Must be an anonymous class (has a body)
            if (creation.getAnonymousClassBody().isEmpty())
                continue;

            List<MethodDeclaration> methods = creation.getAnonymousClassBody().get()
                    .stream()
                    .filter(m -> m instanceof MethodDeclaration)
                    .map(m -> (MethodDeclaration) m)
                    .toList();

            // Only convert single-abstract-method anonymous classes
            if (methods.size() != 1)
                continue;

            MethodDeclaration method = methods.get(0);
            if (method.getBody().isEmpty())
                continue;

            BlockStmt body = method.getBody().get();
            NodeList<Parameter> params = method.getParameters();

            // Build the lambda body
            Statement lambdaBodyStmt = buildLambdaBody(body);

            LambdaExpr lambda = new LambdaExpr(
                    new NodeList<>(params),
                    lambdaBodyStmt,
                    params.size() != 1 // isEnclosingParameters -> true when 0 or 2+ params
            );

            creation.replace(lambda);
            return true;
        }

        return false;
    }

    private Statement buildLambdaBody(BlockStmt body) {
        List<Statement> stmts = body.getStatements();

        if (stmts.size() == 1) {
            Statement stmt = stmts.get(0);
            if (stmt instanceof ReturnStmt ret && ret.getExpression().isPresent()) {
                return new ExpressionStmt(ret.getExpression().get().clone());
            }
            if (stmt instanceof ExpressionStmt) {
                return stmt.clone();
            }
        }
        return body.clone();
    }
}
