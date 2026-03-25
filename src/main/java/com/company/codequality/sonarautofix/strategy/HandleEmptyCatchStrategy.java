package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.*;
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

        // ---- HANDLE EMPTY TRY BLOCK ----
        for (TryStmt tryStmt : cu.findAll(TryStmt.class)) {

            BlockStmt tryBlock = tryStmt.getTryBlock();

            if (tryBlock.getStatements().isEmpty()) {

                // Add a dummy statement WITH comment (Sonar-safe)
                EmptyStmt emptyStmt = new EmptyStmt();
                emptyStmt.setLineComment(" intentionally left empty ");

                tryBlock.addStatement(emptyStmt);

                fixed = true;
            }
        }

        // ---- HANDLE EMPTY CATCH BLOCK ----
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
        
        for (IfStmt ifStmt : cu.findAll(IfStmt.class)) {

            Statement thenStmt = ifStmt.getThenStmt();

            if (thenStmt.isBlockStmt()) {

                BlockStmt block = thenStmt.asBlockStmt();

                if (block.getStatements().isEmpty()) {

                    // CASE 1: if condition is always true → REMOVE
                    if (ifStmt.getCondition().isBooleanLiteralExpr() &&
                        ifStmt.getCondition().asBooleanLiteralExpr().getValue()) {

                        ifStmt.remove(); // clean removal
                    } else {
                        // CASE 2: unknown condition → add meaningful comment
                        EmptyStmt emptyStmt = new EmptyStmt();
                        emptyStmt.setLineComment(" TODO: implement logic ");

                        block.addStatement(emptyStmt);
                    }
                }
            }
        }
        
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {

            // Skip blocks that belong to structured statements
            if (block.getParentNode().isPresent()) {
                if (block.getParentNode().get() instanceof IfStmt ||
                    block.getParentNode().get() instanceof TryStmt ||
                    block.getParentNode().get() instanceof CatchClause ||
                    block.getParentNode().get() instanceof MethodDeclaration ||
                    block.getParentNode().get() instanceof ForStmt ||
                    block.getParentNode().get() instanceof WhileStmt ||
                    block.getParentNode().get() instanceof DoStmt ||
                    block.getParentNode().get() instanceof SwitchStmt) {
                    continue;
                }
            }

            // If it's truly standalone and empty → remove it
            if (block.getStatements().isEmpty()) {
                block.remove();
            }
        }

        return fixed;
    }
}