package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class SimplifyReturnStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.SIMPLIFY_RETURN;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        for (com.github.javaparser.ast.stmt.IfStmt ifStmt : cu.findAll(com.github.javaparser.ast.stmt.IfStmt.class)) {
            if (literalMatch(ifStmt, line)) {
                com.github.javaparser.ast.stmt.Statement thenStmt = ifStmt.getThenStmt();
                com.github.javaparser.ast.stmt.Statement elseStmt = ifStmt.getElseStmt().orElse(null);

                // Pattern 1: if (cond) return true; else return false;
                if (isReturnTrue(thenStmt) && isReturnFalse(elseStmt)) {
                    ifStmt.replace(new com.github.javaparser.ast.stmt.ReturnStmt(ifStmt.getCondition().clone()));
                    return true;
                }

                // Pattern 2: if (cond) return false; else return true;
                if (isReturnFalse(thenStmt) && isReturnTrue(elseStmt)) {
                    ifStmt.replace(new com.github.javaparser.ast.stmt.ReturnStmt(
                            new com.github.javaparser.ast.expr.UnaryExpr(ifStmt.getCondition().clone(),
                                    com.github.javaparser.ast.expr.UnaryExpr.Operator.LOGICAL_COMPLEMENT)));
                    return true;
                }

                // Pattern 3: if (cond) return true; return false; (no else, followed by return
                // false)
                if (elseStmt == null && isReturnTrue(thenStmt)) {
                    var parentNode = ifStmt.getParentNode().orElse(null);
                    if (parentNode instanceof com.github.javaparser.ast.stmt.BlockStmt parent) {
                        int idx = parent.getStatements().indexOf(ifStmt);
                        if (idx != -1 && idx + 1 < parent.getStatements().size()) {
                            com.github.javaparser.ast.stmt.Statement next = parent.getStatement(idx + 1);
                            if (isReturnFalse(next)) {
                                ifStmt.replace(
                                        new com.github.javaparser.ast.stmt.ReturnStmt(ifStmt.getCondition().clone()));
                                next.remove();
                                return true;
                            }
                        }
                    }
                }

                // Pattern 4: if (cond) return false; return true; (no else, followed by return
                // true)
                if (elseStmt == null && isReturnFalse(thenStmt)) {
                    var parentNode = ifStmt.getParentNode().orElse(null);
                    if (parentNode instanceof com.github.javaparser.ast.stmt.BlockStmt parent) {
                        int idx = parent.getStatements().indexOf(ifStmt);
                        if (idx != -1 && idx + 1 < parent.getStatements().size()) {
                            com.github.javaparser.ast.stmt.Statement next = parent.getStatement(idx + 1);
                            if (isReturnTrue(next)) {
                                ifStmt.replace(new com.github.javaparser.ast.stmt.ReturnStmt(
                                        new com.github.javaparser.ast.expr.UnaryExpr(ifStmt.getCondition().clone(),
                                                com.github.javaparser.ast.expr.UnaryExpr.Operator.LOGICAL_COMPLEMENT)));
                                next.remove();
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean literalMatch(com.github.javaparser.ast.stmt.IfStmt ifStmt, int line) {
        return ifStmt.getRange().map(range -> {
            // Match if reported line is within the if block or just before/after
            return (range.begin.line <= line && range.end.line >= line)
                    || (Math.abs(range.begin.line - line) <= 1);
        }).orElse(false);
    }

    private boolean isReturnTrue(com.github.javaparser.ast.stmt.Statement stmt) {
        return isReturnWithLiteral(stmt, true);
    }

    private boolean isReturnFalse(com.github.javaparser.ast.stmt.Statement stmt) {
        return isReturnWithLiteral(stmt, false);
    }

    private boolean isReturnWithLiteral(com.github.javaparser.ast.stmt.Statement stmt, boolean value) {
        if (stmt == null)
            return false;

        if (stmt instanceof com.github.javaparser.ast.stmt.BlockStmt block) {
            if (block.getStatements().size() == 1) {
                stmt = block.getStatement(0);
            } else {
                return false;
            }
        }

        if (stmt instanceof com.github.javaparser.ast.stmt.ReturnStmt rs) {
            return rs.getExpression().map(expr -> expr instanceof com.github.javaparser.ast.expr.BooleanLiteralExpr ble
                    && ble.getValue() == value).orElse(false);
        }
        return false;
    }
}