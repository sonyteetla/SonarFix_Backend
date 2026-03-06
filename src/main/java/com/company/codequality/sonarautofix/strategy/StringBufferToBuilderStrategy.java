package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Component;

@Component
public class StringBufferToBuilderStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.STRINGBUFFER_TO_STRINGBUILDER;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {

            if (line != -1 && type.getBegin().isPresent()
                    && type.getBegin().get().line != line) {
                continue;
            }

            if (type.getNameAsString().equals("StringBuffer")
                    && !type.getNameAsString().equals("StringBuilder")) {

                type.setName("StringBuilder");

                fixed = true;
            }
        }

        return fixed;
    }
}