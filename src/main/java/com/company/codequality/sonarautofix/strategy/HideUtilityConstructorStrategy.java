package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
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

        for (ClassOrInterfaceDeclaration clazz :
                cu.findAll(ClassOrInterfaceDeclaration.class)) {

            if (!clazz.isPublic()) continue;

            // Only utility class (all methods static)
            boolean allStatic = clazz.getMethods()
                    .stream()
                    .allMatch(m -> m.isStatic());

            if (!allStatic) continue;

            // Skip if constructor already exists
            boolean hasPrivateConstructor =
                    clazz.getConstructors()
                            .stream()
                            .anyMatch(ConstructorDeclaration::isPrivate);

            if (hasPrivateConstructor) continue;

            ConstructorDeclaration constructor =
                    clazz.addConstructor();

            constructor.setPrivate(true);
            constructor.setBody(new com.github.javaparser.ast.stmt.BlockStmt());

            fixed = true;
        }

        return fixed;
    }
}