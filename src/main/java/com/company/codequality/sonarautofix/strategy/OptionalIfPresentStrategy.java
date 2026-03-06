package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class OptionalIfPresentStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.OPTIONAL_IF_PRESENT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        // Pattern 1: if (opt.isPresent()) { return opt.get(); } return other;
        for (com.github.javaparser.ast.stmt.IfStmt ifStmt : cu.findAll(com.github.javaparser.ast.stmt.IfStmt.class)) {
            if (ifStmt.getRange().isPresent() && ifStmt.getRange().get().begin.line <= line
                    && ifStmt.getRange().get().end.line >= line) {

                com.github.javaparser.ast.expr.MethodCallExpr condCall = findIsPresentCall(ifStmt.getCondition());
                if (condCall != null && condCall.getScope().isPresent()) {
                    com.github.javaparser.ast.expr.Expression scope = condCall.getScope().get();

                    com.github.javaparser.ast.stmt.ReturnStmt catchGet = findReturnGet(ifStmt.getThenStmt(), scope);
                    if (catchGet != null) {
                        // We found if (opt.isPresent()) { return opt.get(); }
                        // Now look for the following return for the fallback
                        com.github.javaparser.ast.stmt.BlockStmt parent = (com.github.javaparser.ast.stmt.BlockStmt) ifStmt
                                .findAncestor(com.github.javaparser.ast.stmt.BlockStmt.class).orElse(null);
                        if (parent != null) {
                            int idx = parent.getStatements().indexOf(ifStmt);
                            if (idx != -1 && idx + 1 < parent.getStatements().size()) {
                                com.github.javaparser.ast.stmt.Statement next = parent.getStatement(idx + 1);
                                if (next instanceof com.github.javaparser.ast.stmt.ReturnStmt rs
                                        && rs.getExpression().isPresent()) {
                                    // Found: return other;
                                    com.github.javaparser.ast.expr.Expression other = rs.getExpression().get();
                                    com.github.javaparser.ast.expr.MethodCallExpr orElse = new com.github.javaparser.ast.expr.MethodCallExpr(
                                            scope.clone(), "orElse")
                                            .addArgument(other.clone());

                                    ifStmt.replace(new com.github.javaparser.ast.stmt.ReturnStmt(orElse));
                                    next.remove();
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pattern 2: Lone opt.get() -> opt.orElseThrow()
        for (com.github.javaparser.ast.expr.MethodCallExpr call : cu
                .findAll(com.github.javaparser.ast.expr.MethodCallExpr.class)) {
            if (call.getRange().isPresent() && call.getRange().get().begin.line <= line
                    && call.getRange().get().end.line >= line) {
                if (call.getNameAsString().equals("get") && call.getScope().isPresent()) {
                    com.github.javaparser.ast.expr.Expression scope = call.getScope().get();
                    // On the exact reported line, we trust Sonar and replace it
                    call.replace(new com.github.javaparser.ast.expr.MethodCallExpr(scope.clone(), "orElseThrow"));
                    return true;
                }
            }
        }

        return false;
    }

    private com.github.javaparser.ast.expr.MethodCallExpr findIsPresentCall(
            com.github.javaparser.ast.expr.Expression expr) {
        if (expr instanceof com.github.javaparser.ast.expr.MethodCallExpr mce
                && mce.getNameAsString().equals("isPresent")) {
            return mce;
        }
        return null;
    }

    private com.github.javaparser.ast.stmt.ReturnStmt findReturnGet(com.github.javaparser.ast.stmt.Statement stmt,
            com.github.javaparser.ast.expr.Expression scope) {
        if (stmt instanceof com.github.javaparser.ast.stmt.BlockStmt block && block.getStatements().size() == 1) {
            stmt = block.getStatement(0);
        }
        if (stmt instanceof com.github.javaparser.ast.stmt.ReturnStmt rs && rs.getExpression().isPresent()) {
            if (rs.getExpression().get() instanceof com.github.javaparser.ast.expr.MethodCallExpr mce &&
                    mce.getNameAsString().equals("get") &&
                    mce.getScope().isPresent() &&
                    mce.getScope().get().toString().equals(scope.toString())) {
                return rs;
            }
        }
        return null;
    }

}
