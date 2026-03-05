package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ReferenceType;
import org.springframework.stereotype.Component;

import java.util.Iterator;

@Component
public class ReplaceGenericExceptionStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REPLACE_GENERIC_EXCEPTION;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        boolean fixed = false;

        // 1️⃣ Replace new Exception() → RuntimeException
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            if ("Exception".equals(expr.getType().asString())) {
                expr.setType("RuntimeException");
                fixed = true;
            }
        }

        // 2️⃣ Replace throws Exception
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {

            Iterator<ReferenceType> iterator =
                    method.getThrownExceptions().iterator();

            while (iterator.hasNext()) {
                ReferenceType type = iterator.next();
                if ("Exception".equals(type.asString())) {
                    iterator.remove();
                    fixed = true;
                }
            }
        }

        // 3️⃣ Replace catch (Exception e) → catch (RuntimeException e)
        for (CatchClause catchClause : cu.findAll(CatchClause.class)) {
            if ("Exception".equals(
                    catchClause.getParameter().getType().asString())) {

                catchClause.getParameter().setType("RuntimeException");
                fixed = true;
            }
        }

        return fixed;
    }
}