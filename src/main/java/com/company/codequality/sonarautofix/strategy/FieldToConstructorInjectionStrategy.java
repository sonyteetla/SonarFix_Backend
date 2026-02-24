package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.springframework.stereotype.Component;

@Component
public class FieldToConstructorInjectionStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.FIELD_TO_CONSTRUCTOR_INJECTION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (AnnotationExpr ann : cu.findAll(AnnotationExpr.class)) {

            if (ann.getNameAsString().equals("Autowired")) {
                ann.remove(); // Remove field injection
                fixed = true;
            }
        }

        return fixed;
    }
}