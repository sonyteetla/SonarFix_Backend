package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RemoveDeadAssignmentStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_DEAD_ASSIGNMENT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int startLine) {

        for (VariableDeclarator var : cu.findAll(VariableDeclarator.class)) {

            if (var.getRange().isEmpty())
                continue;

            var range = var.getRange().get();

            if (range.begin.line > startLine || range.end.line < startLine)
                continue;

            if (var.getInitializer().isEmpty())
                continue;

            Optional<BlockStmt> blockOpt = var.findAncestor(BlockStmt.class);

            if (blockOpt.isEmpty())
                continue;

            BlockStmt block = blockOpt.get();

            Statement declStmt = var.findAncestor(Statement.class).orElse(null);

            if (declStmt == null)
                continue;

            int index = block.getStatements().indexOf(declStmt);

            if (index == -1 || index + 1 >= block.getStatements().size())
                continue;

            Statement next = block.getStatement(index + 1);

            if (!(next instanceof ExpressionStmt exprStmt))
                continue;

            if (!(exprStmt.getExpression() instanceof AssignExpr assign))
                continue;

            if (assign.getOperator() != AssignExpr.Operator.ASSIGN)
                continue;

            if (!(assign.getTarget() instanceof NameExpr name))
                continue;

            if (!name.getNameAsString().equals(var.getNameAsString()))
                continue;

            // ensure RHS does not use old value
            boolean selfUsage =
                    assign.getValue()
                          .findAll(NameExpr.class)
                          .stream()
                          .anyMatch(n -> n.getNameAsString()
                          .equals(var.getNameAsString()));

            if (selfUsage)
                continue;

            // Dead initializer detected

            Expression newValue = assign.getValue().clone();

            // Replace declaration initializer
            var.setInitializer(newValue);

            // Remove assignment statement
            exprStmt.remove();

            return true;
        }

        return false;
    }
}