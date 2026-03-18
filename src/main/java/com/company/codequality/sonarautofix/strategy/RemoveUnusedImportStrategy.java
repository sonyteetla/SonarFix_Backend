package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class RemoveUnusedImportStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_UNUSED_IMPORT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        Set<String> usedNames = collectUsedNames(cu);

        for (ImportDeclaration imp : cu.getImports()) {

            if (!imp.getBegin().isPresent()) {
                continue;
            }

            if (imp.getBegin().get().line != line) {
                continue;
            }

            String importName = imp.getNameAsString();

            // wildcard imports
            if (imp.isAsterisk()) {

                String packageName = importName;

                boolean used = usedNames.stream()
                        .anyMatch(name -> name.startsWith(packageName));

                if (!used) {
                    imp.remove();
                    fixed = true;
                }

                continue;
            }

            String simpleName =
                    importName.substring(importName.lastIndexOf('.') + 1);

            if (!usedNames.contains(simpleName)) {
                imp.remove();
                fixed = true;
            }
        }

        return fixed;
    }

    private Set<String> collectUsedNames(CompilationUnit cu) {

        Set<String> names = new HashSet<>();

        for (Node node : cu.findAll(Node.class)) {

            if (node instanceof NameExpr n) {
                names.add(n.getNameAsString());
            }

            if (node instanceof ClassOrInterfaceType t) {
                names.add(t.getNameAsString());
            }

            if (node instanceof MethodCallExpr m) {
                names.add(m.getNameAsString());
            }

            if (node instanceof FieldAccessExpr f) {
                names.add(f.getNameAsString());
            }

            if (node instanceof ObjectCreationExpr o) {
                names.add(o.getType().getNameAsString());
            }

            if (node instanceof AnnotationExpr a) {
                names.add(a.getNameAsString());
            }

            if (node instanceof MethodReferenceExpr mr) {
                names.add(mr.getIdentifier());
            }
        }

        return names;
    }
}