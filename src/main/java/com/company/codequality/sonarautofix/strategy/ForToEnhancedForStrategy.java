package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Component;

@Component
public class ForToEnhancedForStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.FOR_TO_ENHANCED_FOR;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (ForStmt forStmt : cu.findAll(ForStmt.class)) {

            try {

                // Must have initialization, comparison, and update
                if (forStmt.getInitialization().isEmpty() ||
                    forStmt.getCompare().isEmpty() ||
                    forStmt.getUpdate().isEmpty()) {
                    continue;
                }

                // Expect initialization: int i = 0
                if (!(forStmt.getInitialization().get(0) instanceof VariableDeclarationExpr init))
                    continue;

                VariableDeclarator var = init.getVariables().get(0);
                String indexVar = var.getNameAsString();

                // Expect condition: i < list.size()
                if (!(forStmt.getCompare().get() instanceof BinaryExpr condition))
                    continue;

                if (condition.getOperator() != BinaryExpr.Operator.LESS)
                    continue;

                if (!(condition.getRight() instanceof MethodCallExpr sizeCall))
                    continue;

                if (!sizeCall.getNameAsString().equals("size"))
                    continue;

                if (!sizeCall.getScope().isPresent())
                    continue;

                String listName = sizeCall.getScope().get().toString();

                // Ensure loop body uses list.get(i)
                boolean foundGetCall = false;

                for (MethodCallExpr call : forStmt.findAll(MethodCallExpr.class)) {

                    if (call.getNameAsString().equals("get") &&
                        call.getScope().isPresent() &&
                        call.getScope().get().toString().equals(listName) &&
                        call.getArguments().size() == 1 &&
                        call.getArgument(0).toString().equals(indexVar)) {

                        foundGetCall = true;
                    }
                }

                if (!foundGetCall)
                    continue;

                // Replace list.get(i) with item
                String loopVar = "item";

                for (MethodCallExpr call : forStmt.findAll(MethodCallExpr.class)) {

                    if (call.getNameAsString().equals("get") &&
                        call.getScope().isPresent() &&
                        call.getScope().get().toString().equals(listName) &&
                        call.getArguments().size() == 1 &&
                        call.getArgument(0).toString().equals(indexVar)) {

                        call.replace(new NameExpr(loopVar));
                    }
                }

                // Create enhanced for-loop
                VariableDeclarator enhancedVar =
                        new VariableDeclarator(
                                new ClassOrInterfaceType(null, "Object"),
                                loopVar
                        );

                ForEachStmt enhanced =
                        new ForEachStmt(
                                new VariableDeclarationExpr(enhancedVar),
                                new NameExpr(listName),
                                forStmt.getBody()
                        );

                forStmt.replace(enhanced);

                fixed = true;

            } catch (Exception ignored) {}
        }

        return fixed;
    }
}