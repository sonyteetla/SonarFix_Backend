package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import org.springframework.stereotype.Component;

@Component
public class AddDefaultCaseStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.ADD_DEFAULT_CASE;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (SwitchStmt stmt : cu.findAll(SwitchStmt.class)) {

            boolean hasDefault =
                    stmt.getEntries().stream().anyMatch(e -> e.getLabels().isEmpty());

            if (!hasDefault) {

                SwitchEntry defaultEntry =
                        new SwitchEntry(
                                new NodeList<>(),
                                SwitchEntry.Type.STATEMENT_GROUP,
                                new NodeList<>(new BreakStmt())
                        );

                stmt.getEntries().add(defaultEntry);

                fixed = true;
            }
        }

        return fixed;
    }
}