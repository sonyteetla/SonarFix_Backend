package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class NoOpFixStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.NONE; // intentional no-op fallback
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        return true; // do nothing but prevent crash
    }
}