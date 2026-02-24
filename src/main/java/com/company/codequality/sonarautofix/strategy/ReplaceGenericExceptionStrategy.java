package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import org.springframework.stereotype.Component;

@Component
public class ReplaceGenericExceptionStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REPLACE_GENERIC_EXCEPTION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        cu.findAll(Parameter.class).forEach(p -> {
            if (p.getType().asString().equals("Exception")) {
                p.setType("RuntimeException");
            }
        });

        return true;
    }
}