package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Component;

@Component
public class RemoveEmptyMethodStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_EMPTY_METHOD;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {

            if (line != -1 && method.getBegin().isPresent()
                    && method.getBegin().get().line != line) {
                continue;
            }

            if (method.getNameAsString().equals("main")) continue;

            if (method.getAnnotationByName("Override").isPresent()) continue;

            if (method.getBody().isPresent()
                    && method.getBody().get().getStatements().isEmpty()) {

                method.remove();
                fixed = true;
            }
        }

        return fixed;
    }
}