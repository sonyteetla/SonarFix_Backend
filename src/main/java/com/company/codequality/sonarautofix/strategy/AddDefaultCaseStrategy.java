package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

/**
 * java:S1132 — Add a default case to switch statements that are missing one.
 *
 * Before: switch (x) { case 1: doA(); break; }
 * After: switch (x) { case 1: doA(); break; default: break; }
 */
@Component
public class AddDefaultCaseStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.ADD_DEFAULT_CASE;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        for (SwitchStmt switchStmt : cu.findAll(SwitchStmt.class)) {
            if (literalMatch(switchStmt, line)) {
                // Check if it already has a default entry
                boolean hasDefault = switchStmt.getEntries().stream()
                        .anyMatch(e -> e.getLabels().isEmpty());
                if (hasDefault)
                    continue;

                // Build: default: break;
                SwitchEntry defaultEntry = new SwitchEntry();
                defaultEntry.setLabels(new com.github.javaparser.ast.NodeList<>()); // ensures 'default'
                defaultEntry.addStatement(new BreakStmt());

                switchStmt.getEntries().add(defaultEntry);
                return true;
            }
        }
        return false;
    }

    private boolean literalMatch(SwitchStmt sw, int line) {
        return sw.getRange().map(range -> {
            return (range.begin.line <= line && range.end.line >= line)
                    || (Math.abs(range.begin.line - line) <= 1);
        }).orElse(false);
    }
}
