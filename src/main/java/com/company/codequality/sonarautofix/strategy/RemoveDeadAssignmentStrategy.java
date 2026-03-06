package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class RemoveDeadAssignmentStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_DEAD_ASSIGNMENT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        // Pattern 1: Initializer in declaration is overwritten
        for (com.github.javaparser.ast.body.VariableDeclarator var : cu
                .findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
            if (var.getRange().isPresent() && var.getRange().get().begin.line <= line
                    && var.getRange().get().end.line >= line) {
                if (var.getInitializer().isPresent()) {
                    if (fixDeadInitializer(var))
                        return true;
                }
            }
        }

        // Pattern 2: Assignment is immediately overwritten
        for (com.github.javaparser.ast.expr.AssignExpr assign : cu
                .findAll(com.github.javaparser.ast.expr.AssignExpr.class)) {
            if (assign.getRange().isPresent() && assign.getRange().get().begin.line <= line
                    && assign.getRange().get().end.line >= line) {
                if (fixDeadAssignment(assign))
                    return true;
            }
        }

        return false;
    }

    private boolean fixDeadInitializer(com.github.javaparser.ast.body.VariableDeclarator var) {
        com.github.javaparser.ast.stmt.BlockStmt parent = (com.github.javaparser.ast.stmt.BlockStmt) var
                .findAncestor(com.github.javaparser.ast.stmt.BlockStmt.class).orElse(null);
        if (parent == null)
            return false;

        com.github.javaparser.ast.stmt.Statement stmt = var.findAncestor(com.github.javaparser.ast.stmt.Statement.class)
                .orElse(null);
        if (stmt == null)
            return false;

        int idx = parent.getStatements().indexOf(stmt);
        if (idx != -1 && idx + 1 < parent.getStatements().size()) {
            com.github.javaparser.ast.stmt.Statement next = parent.getStatement(idx + 1);
            if (next instanceof com.github.javaparser.ast.stmt.ExpressionStmt es
                    && es.getExpression() instanceof com.github.javaparser.ast.expr.AssignExpr nextAssign) {
                if (nextAssign.getTarget().toString().equals(var.getNameAsString())) {
                    // Overwritten!
                    var.setInitializer(nextAssign.getValue().clone());
                    next.remove();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean fixDeadAssignment(com.github.javaparser.ast.expr.AssignExpr assign) {
        com.github.javaparser.ast.stmt.BlockStmt parent = (com.github.javaparser.ast.stmt.BlockStmt) assign
                .findAncestor(com.github.javaparser.ast.stmt.BlockStmt.class).orElse(null);
        if (parent == null)
            return false;

        com.github.javaparser.ast.stmt.Statement stmt = assign
                .findAncestor(com.github.javaparser.ast.stmt.Statement.class).orElse(null);
        if (stmt == null)
            return false;

        int idx = parent.getStatements().indexOf(stmt);
        if (idx != -1 && idx + 1 < parent.getStatements().size()) {
            com.github.javaparser.ast.stmt.Statement next = parent.getStatement(idx + 1);
            if (next instanceof com.github.javaparser.ast.stmt.ExpressionStmt es
                    && es.getExpression() instanceof com.github.javaparser.ast.expr.AssignExpr nextAssign) {
                if (nextAssign.getTarget().toString().equals(assign.getTarget().toString())) {
                    // The first assignment is dead
                    stmt.remove();
                    return true;
                }
            }
        }
        return false;
    }
}