package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.Parameter;
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

            // Skip if position unknown
            if (!imp.getBegin().isPresent()) {
                continue;
            }

            int importLine = imp.getBegin().get().line;

            // Tolerate ±1 line offset between Sonar (0-indexed or end-line)
            // and JavaParser (1-indexed start-line)
            if (Math.abs(importLine - line) > 1) {
                continue;
            }

            String importName = imp.getNameAsString();

            // ── Wildcard import: e.g. import java.util.*
            if (imp.isAsterisk()) {
                boolean used = usedNames.stream()
                        .anyMatch(name -> name.startsWith(importName));
                if (!used) {
                    imp.remove();
                    fixed = true;
                }
                continue;
            }

            // ── Static import: e.g. import static org.junit.Assert.assertEquals
            // The usable simple name is the last segment (the method/field name)
            if (imp.isStatic()) {
                String memberName = importName.substring(importName.lastIndexOf('.') + 1);
                if (!usedNames.contains(memberName)) {
                    imp.remove();
                    fixed = true;
                }
                continue;
            }

            // ── Regular import: e.g. import org.springframework.data.repository.query.Param
            // Simple name = last segment after the final dot
            String simpleName = importName.substring(importName.lastIndexOf('.') + 1);
            if (!usedNames.contains(simpleName)) {
                imp.remove();
                fixed = true;
            }
        }

        return fixed;
    }

    /**
     * Walks every node in the CompilationUnit and collects every
     * simple name that is actually referenced in the source.
     *
     * Covers:
     *  - Plain name references          (NameExpr)
     *  - Type references                (ClassOrInterfaceType)
     *  - Method calls                   (MethodCallExpr)
     *  - Field / static access          (FieldAccessExpr  + scope chain)
     *  - Object creation                (ObjectCreationExpr)
     *  - Class-level annotations        (AnnotationExpr)
     *  - Parameter-level annotations    (Parameter → annotations)
     *  - Method references              (MethodReferenceExpr)
     */
    private Set<String> collectUsedNames(CompilationUnit cu) {
        Set<String> names = new HashSet<>();

        for (Node node : cu.findAll(Node.class)) {

            // Plain name: variable / type / package reference
            if (node instanceof NameExpr n) {
                names.add(n.getNameAsString());
            }

            // Type in declarations, generics, casts, etc.
            // getNameAsString() always returns only the simple/last segment
            if (node instanceof ClassOrInterfaceType t) {
                names.add(t.getNameAsString());
            }

            // Method invocation name
            if (node instanceof MethodCallExpr m) {
                names.add(m.getNameAsString());
            }

            // Field or static member access: SomeClass.FIELD
            // Add both the field name and walk up the scope chain
            if (node instanceof FieldAccessExpr f) {
                names.add(f.getNameAsString());
                addScopeRootName(f.getScope(), names);
            }

            // new ConcurrentHashMap<>(), new ArrayList<>(), etc.
            if (node instanceof ObjectCreationExpr o) {
                names.add(o.getType().getNameAsString());
            }

            // Class / method level annotations: @Service, @Query, @Modifying …
            // getNameAsString() can return a qualified name for rare cases,
            // so always extract the simple (last) segment
            if (node instanceof AnnotationExpr a) {
                names.add(extractSimpleName(a.getNameAsString()));
            }

            // Parameter-level annotations: @Param, @RequestBody, @PathVariable …
            // These live on method / constructor parameters and are NOT visited
            // as standalone AnnotationExpr nodes by findAll in some JP versions,
            // so we pull them explicitly from each Parameter node.
            if (node instanceof Parameter p) {
                p.getAnnotations().forEach(a ->
                        names.add(extractSimpleName(a.getNameAsString()))
                );
            }

            // Method reference identifier: SomeClass::method
            if (node instanceof MethodReferenceExpr mr) {
                names.add(mr.getIdentifier());
            }
        }

        return names;
    }

    /**
     * Recursively walks a field-access scope chain and adds every
     * simple name found, so that "Collections.singletonList" correctly
     * registers "Collections" as a used name.
     */
    private void addScopeRootName(Expression scope, Set<String> names) {
        if (scope instanceof NameExpr n) {
            names.add(n.getNameAsString());
        } else if (scope instanceof FieldAccessExpr f) {
            names.add(f.getNameAsString());
            addScopeRootName(f.getScope(), names);
        }
    }

    /**
     * Returns the simple (unqualified) class name from a potentially
     * fully-qualified name string.
     * "org.springframework.data.repository.query.Param" → "Param"
     * "Param"                                           → "Param"
     */
    private String extractSimpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }
}