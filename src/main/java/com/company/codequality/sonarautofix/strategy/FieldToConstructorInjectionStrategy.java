package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FieldToConstructorInjectionStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.FIELD_TO_CONSTRUCTOR_INJECTION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {

            if (shouldSkipClass(clazz)) {
                continue;
            }

            List<VariableDeclarator> injectableFields = new ArrayList<>();

            for (FieldDeclaration field : clazz.getFields()) {

                if (field.isStatic()) {
                    continue;
                }

                if (field.getAnnotationByName("Autowired").isPresent()) {

                    field.getAnnotationByName("Autowired").ifPresent(Node::remove);

                    for (VariableDeclarator var : field.getVariables()) {
                        injectableFields.add(var);
                    }

                    field.addModifier(Modifier.Keyword.FINAL);
                }
            }

            if (injectableFields.isEmpty()) {
                continue;
            }

            ConstructorDeclaration constructor =
                    createOrGetConstructor(clazz);

            BlockStmt body = constructor.getBody();

            for (VariableDeclarator var : injectableFields) {

                String name = var.getNameAsString();

                if (constructor.getParameterByName(name).isEmpty()) {
                    constructor.addParameter(var.getType(), name);
                }

                boolean assignmentExists = body.getStatements()
                        .stream()
                        .anyMatch(s -> s.toString().contains("this." + name));

                if (!assignmentExists) {

                    AssignExpr assign =
                            new AssignExpr(
                                    new FieldAccessExpr(new ThisExpr(), name),
                                    new NameExpr(name),
                                    AssignExpr.Operator.ASSIGN
                            );

                    body.addStatement(assign);
                }
            }

            fixed = true;
        }

     // after processing all classes
        removeAutowiredImportIfUnused(cu);

        return fixed;
    }

    private boolean shouldSkipClass(ClassOrInterfaceDeclaration clazz) {

        for (AnnotationExpr ann : clazz.getAnnotations()) {

            String name = ann.getNameAsString();

            if (name.equals("RequiredArgsConstructor") ||
                name.equals("AllArgsConstructor")) {
                return true;
            }
        }

        return false;
    }

    private ConstructorDeclaration createOrGetConstructor(
            ClassOrInterfaceDeclaration clazz) {

        if (!clazz.getConstructors().isEmpty()) {
            return clazz.getConstructors().get(0);
        }

        ConstructorDeclaration constructor = new ConstructorDeclaration();

        constructor.setName(clazz.getName());
        constructor.addModifier(Modifier.Keyword.PUBLIC);
        constructor.setBody(new BlockStmt());

        List<BodyDeclaration<?>> members = clazz.getMembers();

        int insertIndex = 0;

        // insert constructor before first method
        for (int i = 0; i < members.size(); i++) {

            BodyDeclaration<?> member = members.get(i);

            if (member instanceof MethodDeclaration ||
                member instanceof ConstructorDeclaration ||
                member instanceof ClassOrInterfaceDeclaration) {

                insertIndex = i;
                break;
            }

            insertIndex = i + 1;
        }

        members.add(insertIndex, constructor);

        return constructor;
    }
    
    private void removeAutowiredImportIfUnused(CompilationUnit cu) {

        boolean autowiredStillUsed =
                cu.findAll(AnnotationExpr.class)
                  .stream()
                  .anyMatch(a -> a.getNameAsString().equals("Autowired"));

        if (!autowiredStillUsed) {

            cu.getImports().removeIf(i ->
                    i.getNameAsString()
                     .equals("org.springframework.beans.factory.annotation.Autowired"));
        }
    }
}