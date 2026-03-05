package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Component;

import java.util.Iterator;

@Component
public class RemoveUnusedImportStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_UNUSED_IMPORT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        Iterator<ImportDeclaration> iterator = cu.getImports().iterator();

        while (iterator.hasNext()) {

            ImportDeclaration imp = iterator.next();

            // Skip wildcard imports (safe approach)
            if (imp.isAsterisk()) continue;

            String simpleName = imp.getName().getIdentifier();

            boolean used = false;

            // Check NameExpr usage
            for (NameExpr name : cu.findAll(NameExpr.class)) {
                if (name.getNameAsString().equals(simpleName)) {
                    used = true;
                    break;
                }
            }

            // Check type usage
            if (!used) {
                for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                    if (type.getNameAsString().equals(simpleName)) {
                        used = true;
                        break;
                    }
                }
            }

            if (!used) {
                iterator.remove();
                fixed = true;
            }
        }

        return fixed;
    }
}