package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RemoveUnusedVariableStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_UNUSED_VARIABLE;
    }

    @Override
    public boolean apply(CompilationUnit cu, int startLine) {

        boolean modified = false;

        for (VariableDeclarator var : cu.findAll(VariableDeclarator.class)) {

            if (var.getRange().isEmpty())
                continue;

            int line = var.getRange().get().begin.line;

            if (line != startLine)
                continue;

            String varName = var.getNameAsString();

            // find all usages
            List<NameExpr> usages = cu.findAll(NameExpr.class,
                    n -> n.getNameAsString().equals(varName));

            for (NameExpr usage : usages) {

                usage.getParentNode().ifPresent(parent -> {

                    if (parent instanceof AssignExpr assign) {

                        Node grand = assign.getParentNode().orElse(null);

                        // case 1: assignment inside condition
                        if (!(grand instanceof ExpressionStmt)) {

                            assign.replace(assign.getValue());

                        }
                        // case 2: normal assignment statement
                        else {

                            grand.remove();

                        }
                    }
                });
            }

            // remove declaration
            var.getParentNode().ifPresent(parent -> {

                if (parent instanceof VariableDeclarationExpr decl) {

                    if (decl.getVariables().size() == 1) {

                        decl.getParentNode().ifPresent(grand -> {

                            if (grand instanceof ExpressionStmt stmt) {
                                stmt.remove();
                            }

                        });

                    } else {

                        var.remove();

                    }
                }

            });

            modified = true;
        }

        return modified;
    }
}