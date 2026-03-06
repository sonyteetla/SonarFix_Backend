package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * java:S1612 — Replace lambdas with method references where possible.
 *
 * Pattern 1: (x) -> x.method() → Type::method (instance method ref)
 * Pattern 2: (x) -> obj.method(x) → obj::method (bound method ref)
 */
@Component
public class LambdaToMethodReferenceStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.LAMBDA_TO_METHOD_REFERENCE;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        for (LambdaExpr lambda : cu.findAll(LambdaExpr.class)) {
            if (literalMatch(lambda, line)) {
                Expression body = extractBodyExpression(lambda);
                if (body == null)
                    continue;

                if (body instanceof MethodCallExpr call) {
                    MethodReferenceExpr ref = tryConvert(call, lambda.getParameters());
                    if (ref != null) {
                        lambda.replace(ref);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean literalMatch(LambdaExpr lambda, int line) {
        return lambda.getRange().map(range -> range.begin.line <= line && range.end.line >= line).orElse(false);
    }

    private Expression extractBodyExpression(LambdaExpr lambda) {
        if (lambda.getExpressionBody().isPresent()) {
            return lambda.getExpressionBody().get();
        }
        if (lambda.getBody() instanceof com.github.javaparser.ast.stmt.BlockStmt block) {
            var stmts = block.getStatements();
            if (stmts.size() == 1) {
                if (stmts.get(0).isReturnStmt() && stmts.get(0).asReturnStmt().getExpression().isPresent()) {
                    return stmts.get(0).asReturnStmt().getExpression().get();
                }
                if (stmts.get(0).isExpressionStmt()) {
                    return stmts.get(0).asExpressionStmt().getExpression();
                }
            }
        }
        return null;
    }

    private MethodReferenceExpr tryConvert(MethodCallExpr call, List<Parameter> params) {
        if (params.isEmpty())
            return null;
        String methodName = call.getNameAsString();
        List<Expression> args = call.getArguments();

        // Pattern 1: (x) -> x.method() -> Type::method
        if (params.size() == 1 && args.isEmpty() && call.getScope().isPresent()) {
            Expression scope = call.getScope().get();
            if (scope.isNameExpr() && scope.asNameExpr().getNameAsString().equals(params.get(0).getNameAsString())) {
                // If it's x.method(), we need the type of x for Type::method
                try {
                    var type = scope.calculateResolvedType();
                    if (type.isReferenceType()) {
                        String typeName = type.asReferenceType().getTypeDeclaration().get().getName();
                        return new MethodReferenceExpr(new NameExpr(typeName), new NodeList<>(), methodName);
                    }
                } catch (Exception e) {
                    // Fallback to the scope itself if type resolution fails (might result in
                    // instance::method if scope isn't a type)
                    return new MethodReferenceExpr(scope.clone(), new NodeList<>(), methodName);
                }
            }
        }

        // Pattern 2: (x) -> obj.method(x) -> obj::method
        if (params.size() == args.size() && call.getScope().isPresent()) {
            boolean allArgsMatch = true;
            for (int i = 0; i < params.size(); i++) {
                Expression arg = args.get(i);
                if (!arg.isNameExpr() || !arg.asNameExpr().getNameAsString().equals(params.get(i).getNameAsString())) {
                    allArgsMatch = false;
                    break;
                }
            }
            if (allArgsMatch) {
                return new MethodReferenceExpr(call.getScope().get().clone(), new NodeList<>(), methodName);
            }
        }

        return null;
    }
}
