package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class FieldToConstructorInjectionStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.FIELD_TO_CONSTRUCTOR_INJECTION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        for (com.github.javaparser.ast.body.FieldDeclaration field : cu
                .findAll(com.github.javaparser.ast.body.FieldDeclaration.class)) {

            if (field.getRange().isEmpty())
                continue;
            var range = field.getRange().get();

            // Match if issue line is anywhere within the field declaration
            if (range.begin.line > line || range.end.line < line) {
                continue;
            }

            // Identify injection annotation
            com.github.javaparser.ast.expr.AnnotationExpr injectionAnn = null;
            boolean isValue = false;

            for (com.github.javaparser.ast.expr.AnnotationExpr ann : field.getAnnotations()) {
                String name = ann.getNameAsString();
                if (name.equals("Autowired") || name.endsWith(".Autowired") ||
                        name.equals("Inject") || name.endsWith(".Inject")) {
                    injectionAnn = ann;
                    break;
                }
                if (name.equals("Value") || name.endsWith(".Value")) {
                    injectionAnn = ann;
                    isValue = true;
                    break;
                }
            }

            if (injectionAnn == null)
                continue;

            // Store value annotation if present to move to constructor parameter
            com.github.javaparser.ast.expr.AnnotationExpr paramAnn = isValue ? injectionAnn.clone() : null;

            // 1. Remove injection annotation from field
            injectionAnn.remove();

            // 2. Make field private final
            field.setPrivate(true);
            if (!field.isFinal() && !field.isStatic()) {
                field.addModifier(com.github.javaparser.ast.Modifier.Keyword.FINAL);
            }

            // 3. Find parent class
            field.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                    .ifPresent(clazz -> {
                        java.util.List<com.github.javaparser.ast.body.ConstructorDeclaration> constructors = clazz
                                .getConstructors();

                        com.github.javaparser.ast.body.VariableDeclarator var = field.getVariable(0);
                        String varName = var.getNameAsString();
                        com.github.javaparser.ast.type.Type varType = var.getType();

                        com.github.javaparser.ast.body.ConstructorDeclaration cd;
                        if (constructors.isEmpty()) {
                            cd = clazz.addConstructor(com.github.javaparser.ast.Modifier.Keyword.PUBLIC);
                        } else {
                            cd = constructors.get(0);
                        }

                        // Ensure parameter exists
                        boolean exists = cd.getParameters().stream().anyMatch(p -> p.getNameAsString().equals(varName));
                        if (!exists) {
                            cd.addParameter(varType, varName);
                            com.github.javaparser.ast.body.Parameter param = cd
                                    .getParameter(cd.getParameters().size() - 1);
                            if (paramAnn != null) {
                                param.addAnnotation(paramAnn);
                            }
                        }

                        // Always add assignment to ensure field is initialized
                        boolean assignmentExists = cd.getBody().getStatements().stream()
                                .filter(s -> s.isExpressionStmt())
                                .map(s -> s.asExpressionStmt().getExpression())
                                .filter(e -> e.isAssignExpr())
                                .map(e -> e.asAssignExpr())
                                .anyMatch(ae -> ae.getTarget().toString().equals("this." + varName));

                        if (!assignmentExists) {
                            cd.getBody().addStatement(new com.github.javaparser.ast.stmt.ExpressionStmt(
                                    new com.github.javaparser.ast.expr.AssignExpr(
                                            new com.github.javaparser.ast.expr.FieldAccessExpr(
                                                    new com.github.javaparser.ast.expr.ThisExpr(), varName),
                                            new com.github.javaparser.ast.expr.NameExpr(varName),
                                            com.github.javaparser.ast.expr.AssignExpr.Operator.ASSIGN)));
                        }
                    });

            return true;
        }

        return false;
    }
}