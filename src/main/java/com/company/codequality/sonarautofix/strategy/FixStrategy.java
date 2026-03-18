package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;

public interface FixStrategy {

    FixType getFixType();

    boolean apply(CompilationUnit cu, int line);

    /**
     * Optional extended apply method that includes the project path.
     * Strategies that need to modify pom.xml (e.g., add SLF4J dependency)
     * should override this method.
     */
    default boolean apply(CompilationUnit cu, int line, String projectPath) {
        return apply(cu, line);
    }
}