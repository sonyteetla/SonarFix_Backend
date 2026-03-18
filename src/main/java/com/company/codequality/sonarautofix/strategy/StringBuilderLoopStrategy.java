package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class StringBuilderLoopStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.STRING_BUILDER_LOOP;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {

            if (!method.getBody().isPresent()) continue;

            VariableDeclarator stringVar = null;

            // Find String variable initialized to ""
            for (VariableDeclarator v : method.findAll(VariableDeclarator.class)) {
                if (v.getType().asString().equals("String")
                        && v.getInitializer().isPresent()
                        && v.getInitializer().get().toString().equals("\"\"")) {
                    stringVar = v;
                    break;
                }
            }

            if (stringVar == null) continue;

            String varName = stringVar.getNameAsString();
            String builderName = varName + "Builder";

            boolean concatFound = false;

            List<Statement> loops = new ArrayList<>();
            loops.addAll(method.findAll(ForStmt.class));
            loops.addAll(method.findAll(ForEachStmt.class));

            for (Statement loop : loops) {

                for (AssignExpr assign : loop.findAll(AssignExpr.class)) {

                    if (!assign.getTarget().toString().equals(varName)) continue;

                    List<Expression> parts = new ArrayList<>();

                    if (assign.getOperator() == AssignExpr.Operator.PLUS) {

                        parts.add(assign.getValue());

                    } else if (assign.getOperator() == AssignExpr.Operator.ASSIGN
                            && assign.getValue() instanceof BinaryExpr) {

                        BinaryExpr bin = (BinaryExpr) assign.getValue();

                        if (bin.getOperator() == BinaryExpr.Operator.PLUS) {
                            flatten(bin, parts);
                            parts.removeIf(p -> p.toString().equals(varName));
                        }
                    }

                    if (parts.isEmpty()) continue;

                    StringBuilder code = new StringBuilder(builderName);

                    for (Expression p : parts) {
                        code.append(".append(").append(p.toString()).append(")");
                    }

                    code.append(";");

                    assign.replace(
                            StaticJavaParser.parseStatement(code.toString())
                    );

                    concatFound = true;
                }
            }

            if (!concatFound) continue;

            // Insert StringBuilder
            method.getBody().get().addStatement(
                    0,
                    StaticJavaParser.parseStatement(
                            "StringBuilder " + builderName + " = new StringBuilder();"
                    )
            );

            // Remove original String variable
            stringVar.remove();

            // Replace return result → resultBuilder.toString()
            for (ReturnStmt ret : method.findAll(ReturnStmt.class)) {
                if (ret.getExpression().isPresent()
                        && ret.getExpression().get().toString().equals(varName)) {

                    ret.setExpression(
                            StaticJavaParser.parseExpression(
                                    builderName + ".toString()"
                            )
                    );
                }
            }

            fixed = true;
        }

        return fixed;
    }

    private void flatten(Expression expr, List<Expression> parts) {

        if (expr instanceof BinaryExpr) {

            BinaryExpr b = (BinaryExpr) expr;

            if (b.getOperator() == BinaryExpr.Operator.PLUS) {
                flatten(b.getLeft(), parts);
                flatten(b.getRight(), parts);
                return;
            }
        }

        parts.add(expr);
    }
}