package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class MethodRenameStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.METHOD_RENAME;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        // ⚠️ Not used here (handled separately via service)
        return false;
    }
}