package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;

public interface FixStrategy {

    FixType getFixType();

    boolean apply(CompilationUnit cu, int line);
}