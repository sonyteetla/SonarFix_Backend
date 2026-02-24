package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class ExtractConstantStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.EXTRACT_CONSTANT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        return true; // safe placeholder
    }
}