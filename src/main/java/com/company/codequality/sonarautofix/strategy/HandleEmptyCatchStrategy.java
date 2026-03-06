package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class HandleEmptyCatchStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.HANDLE_EMPTY_CATCH;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        // Try to match CatchClause directly
        for (com.github.javaparser.ast.stmt.CatchClause catchClause : cu
                .findAll(com.github.javaparser.ast.stmt.CatchClause.class)) {

            if (catchClause.getRange().isPresent()) {
                var range = catchClause.getRange().get();
                if (range.begin.line <= line && range.end.line >= line) {
                    if (fixCatchIfEmpty(cu, catchClause))
                        return true;
                }
            }
        }

        // Sometimes Sonar reports on the TryStmt line for empty catch issues
        for (com.github.javaparser.ast.stmt.TryStmt tryStmt : cu
                .findAll(com.github.javaparser.ast.stmt.TryStmt.class)) {
            if (tryStmt.getRange().isPresent()) {
                var range = tryStmt.getRange().get();
                if (range.begin.line <= line && range.end.line >= line) {
                    for (com.github.javaparser.ast.stmt.CatchClause cc : tryStmt.getCatchClauses()) {
                        if (cc.getBody().getStatements().isEmpty()) {
                            if (fixCatchIfEmpty(cu, cc))
                                return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean fixCatchIfEmpty(CompilationUnit cu, com.github.javaparser.ast.stmt.CatchClause catchClause) {
        if (catchClause.getBody().getStatements().isEmpty()) {
            String exceptionName = catchClause.getParameter().getNameAsString();

            com.github.javaparser.ast.expr.MethodCallExpr logCall = new com.github.javaparser.ast.expr.MethodCallExpr(
                    new com.github.javaparser.ast.expr.NameExpr("log"), "error")
                    .addArgument(new com.github.javaparser.ast.expr.StringLiteralExpr("Exception occurred: {}"))
                    .addArgument(new com.github.javaparser.ast.expr.NameExpr(exceptionName));

            catchClause.getBody().addStatement(logCall);

            com.company.codequality.sonarautofix.util.LoggerUtil.ensureSlf4jLoggerExists(cu);
            return true;
        }
        return false;
    }
}