package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.IfStmt;
import org.springframework.stereotype.Component;

@Component
public class RemoveEmptyIfStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_EMPTY_IF;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (IfStmt ifStmt : cu.findAll(IfStmt.class)) {

            if (line != -1 && ifStmt.getBegin().isPresent()
                    && ifStmt.getBegin().get().line != line) {
                continue;
            }

            // skip if there is else block
            if (ifStmt.getElseStmt().isPresent()) {
                continue;
            }

            if (ifStmt.getThenStmt().isBlockStmt()
                    && ifStmt.getThenStmt()
                    .asBlockStmt()
                    .getStatements()
                    .isEmpty()) {

                try {
                    ifStmt.remove();
                    fixed = true;
                } catch (Exception ignored) {}

            }
        }

        return fixed;
    }
}