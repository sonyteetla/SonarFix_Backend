package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.springframework.stereotype.Component;

@Component
public class HideUtilityConstructorStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.HIDE_UTILITY_CONSTRUCTOR;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {

            if (!clazz.isPublic()) continue;

            if (clazz.getMethods().isEmpty()) continue;

            // Add private constructor if missing
            clazz.addConstructor()
                 .setPrivate(true)
                 .setBody(new com.github.javaparser.ast.stmt.BlockStmt());

            fixed = true;
        }

        return fixed;
    }
}