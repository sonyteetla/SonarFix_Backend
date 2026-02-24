package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class RemoveUnusedImportStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_UNUSED_IMPORT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        if (line <= 0) {
            return false;
        }

        return cu.getImports().removeIf(importDecl ->
                importDecl.getBegin()
                        .map(pos -> pos.line == line)
                        .orElse(false)
        );
    }
}