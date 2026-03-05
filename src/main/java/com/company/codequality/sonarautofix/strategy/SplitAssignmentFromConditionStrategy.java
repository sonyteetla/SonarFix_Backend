package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SplitAssignmentFromConditionStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.SPLIT_ASSIGNMENT_FROM_CONDITION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int startLine) {

        for (IfStmt stmt : cu.findAll(IfStmt.class)) {

            if (!stmt.getRange().isPresent()) continue;

            var range = stmt.getRange().get();
            if (range.begin.line > startLine || range.end.line < startLine)
                continue;

            Expression condition = stmt.getCondition();

            for (AssignExpr assign : condition.findAll(AssignExpr.class)) {

                if (!isSafe(assign, stmt))
                    continue;

                NameExpr target = (NameExpr) assign.getTarget();

                // 🔥 CRITICAL CHECK:
                // Variable must be declared before this statement
                if (!isDeclaredBefore(target, stmt))
                    continue;

                AssignExpr cloned = assign.clone();

                assign.replace(target.clone());

                BlockStmt parentBlock =
                        stmt.findAncestor(BlockStmt.class).orElse(null);

                if (parentBlock == null) continue;

                int index = parentBlock.getStatements().indexOf(stmt);
                if (index == -1) continue;

                parentBlock.addStatement(index,
                        new ExpressionStmt(cloned));

                return true;
            }
        }

        return false;
    }

    private boolean isSafe(AssignExpr assign, IfStmt stmt) {

        // Only simple assignment
        if (assign.getOperator() != AssignExpr.Operator.ASSIGN)
            return false;

        // Only simple variable target
        if (!(assign.getTarget() instanceof NameExpr))
            return false;

        // No lambdas
        if (assign.findAncestor(LambdaExpr.class).isPresent())
            return false;

        // No ternary
        if (assign.findAncestor(ConditionalExpr.class).isPresent())
            return false;

        // No switch expressions
        if (assign.findAncestor(SwitchExpr.class).isPresent())
            return false;

        // No short-circuit AND / OR
        Optional<BinaryExpr> binary =
                assign.findAncestor(BinaryExpr.class);

        if (binary.isPresent()) {

            BinaryExpr b = binary.get();

            if (b.getOperator() == BinaryExpr.Operator.OR ||
                b.getOperator() == BinaryExpr.Operator.AND) {
                return false;
            }
        }

        return true;
    }

    private boolean isDeclaredBefore(NameExpr name, IfStmt stmt) {

        try {
            ResolvedValueDeclaration resolved = name.resolve();

            Optional<BlockStmt> blockOpt =
                    stmt.findAncestor(BlockStmt.class);

            if (blockOpt.isEmpty())
                return false;

            BlockStmt block = blockOpt.get();

            int stmtIndex =
                    block.getStatements().indexOf(stmt);

            for (int i = 0; i < stmtIndex; i++) {

                Statement s = block.getStatement(i);

                for (VariableDeclarator var :
                        s.findAll(VariableDeclarator.class)) {

                    try {
                        if (var.resolve().equals(resolved))
                            return true;
                    } catch (Exception ignored) {}
                }
            }

        } catch (Exception ignored) {}

        return false;
    }
}