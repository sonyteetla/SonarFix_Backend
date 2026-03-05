package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SimplifyReturnStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.SIMPLIFY_RETURN;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (IfStmt ifStmt : cu.findAll(IfStmt.class)) {

            if (!ifStmt.getElseStmt().isPresent())
                continue;

            Optional<ReturnStmt> thenReturnOpt = extractReturn(ifStmt.getThenStmt());
            Optional<ReturnStmt> elseReturnOpt = extractReturn(ifStmt.getElseStmt().get());

            if (thenReturnOpt.isEmpty() || elseReturnOpt.isEmpty())
                continue;

            ReturnStmt thenReturn = thenReturnOpt.get();
            ReturnStmt elseReturn = elseReturnOpt.get();

            if (!thenReturn.getExpression().isPresent()
                    || !elseReturn.getExpression().isPresent())
                continue;

            String thenExpr = thenReturn.getExpression().get().toString();
            String elseExpr = elseReturn.getExpression().get().toString();

            // Case 1: if(cond) return true; else return false;
            if ("true".equals(thenExpr) && "false".equals(elseExpr)) {
                ifStmt.replace(new ReturnStmt(ifStmt.getCondition().clone()));
                fixed = true;
            }

            // Case 2: if(cond) return false; else return true;
            else if ("false".equals(thenExpr) && "true".equals(elseExpr)) {
                ifStmt.replace(
                        new ReturnStmt(
                                new UnaryExpr(
                                        ifStmt.getCondition().clone(),
                                        UnaryExpr.Operator.LOGICAL_COMPLEMENT
                                )
                        )
                );
                fixed = true;
            }
        }

        return fixed;
    }

    private Optional<ReturnStmt> extractReturn(Statement stmt) {

        // Direct return (no braces)
        if (stmt instanceof ReturnStmt) {
            return Optional.of((ReturnStmt) stmt);
        }

        // Block { return ...; }
        if (stmt instanceof BlockStmt) {
            BlockStmt block = (BlockStmt) stmt;
            if (block.getStatements().size() == 1
                    && block.getStatement(0) instanceof ReturnStmt) {
                return Optional.of((ReturnStmt) block.getStatement(0));
            }
        }

        return Optional.empty();
    }
}