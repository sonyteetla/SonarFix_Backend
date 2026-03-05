package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import org.springframework.stereotype.Component;

@Component
public class HandleEmptyCatchStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.HANDLE_EMPTY_CATCH;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        for (CatchClause catchClause : cu.findAll(CatchClause.class)) {

            if (catchClause.getBody().getStatements().isEmpty()) {

                String exceptionVar =
                        catchClause.getParameter().getNameAsString();

                MethodCallExpr printCall =
                        new MethodCallExpr(
                                new NameExpr(exceptionVar),
                                "printStackTrace"
                        );

                BlockStmt newBody = new BlockStmt();
                newBody.addStatement(printCall);

                catchClause.setBody(newBody);
                fixed = true;
            }
        }

        return fixed;
    }
}