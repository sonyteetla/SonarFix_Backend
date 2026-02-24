package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Component;

@Component
public class HandleEmptyCatchStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.HANDLE_EMPTY_CATCH;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        cu.findAll(CatchClause.class).forEach(c -> {
            if (c.getBody().isEmpty()) {
                BlockStmt body = new BlockStmt();
                body.addStatement(new MethodCallExpr(null, "printStackTrace"));
                c.setBody(body);
            }
        });

        return true;
    }
}