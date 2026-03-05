package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import org.springframework.stereotype.Component;

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

            // Find String variable initialized to ""
            String stringVarName = null;

            for (var varDecl : method.findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
                if (varDecl.getType().asString().equals("String")
                        && varDecl.getInitializer().isPresent()
                        && varDecl.getInitializer().get().toString().equals("\"\"")) {

                    stringVarName = varDecl.getNameAsString();
                    varDecl.remove(); // remove original String result = "";
                    break;
                }
            }

            if (stringVarName == null) continue;

            for (ForStmt loop : method.findAll(ForStmt.class)) {

                boolean hasConcat = false;

                for (AssignExpr assign : loop.findAll(AssignExpr.class)) {

                    if (assign.getTarget().toString().equals(stringVarName)
                            && assign.getValue() instanceof BinaryExpr) {

                        BinaryExpr binary = (BinaryExpr) assign.getValue();

                        if (binary.getOperator() == BinaryExpr.Operator.PLUS) {

                            // Replace with sb.append(...)
                            assign.replace(
                                    StaticJavaParser.parseStatement(
                                            "sb.append(" + binary.getRight() + ");"
                                    )
                            );

                            hasConcat = true;
                        }
                    }
                }

                if (hasConcat) {

                    // Add StringBuilder at top of method
                    method.getBody().get().addStatement(
                            0,
                            StaticJavaParser.parseStatement(
                                    "StringBuilder sb = new StringBuilder();"
                            )
                    );

                    // Replace return result → return sb.toString();
                    for (ReturnStmt ret : method.findAll(ReturnStmt.class)) {
                        if (ret.getExpression().isPresent()
                                && ret.getExpression().get().toString().equals(stringVarName)) {

                            ret.setExpression(
                                    new NameExpr("sb.toString()")
                            );
                        }
                    }

                    fixed = true;
                }
            }
        }

        return fixed;
    }
}