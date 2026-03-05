package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ConvertToTryWithResourcesStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.CONVERT_TO_TRY_WITH_RESOURCES;
    }

    @Override
    public boolean apply(CompilationUnit cu, int startLine) {

        for (VariableDeclarationExpr decl : cu.findAll(VariableDeclarationExpr.class)) {

            if (decl.getRange().isEmpty()) continue;

            int line = decl.getRange().get().begin.line;
            if (line != startLine) continue;

            if (decl.getVariables().size() != 1) continue;

            VariableDeclarator var = decl.getVariable(0);

            if (var.getInitializer().isEmpty()) continue;
            if (!(var.getInitializer().get() instanceof ObjectCreationExpr)) continue;

            Optional<BlockStmt> blockOpt = decl.findAncestor(BlockStmt.class);
            if (blockOpt.isEmpty()) continue;

            BlockStmt block = blockOpt.get();

            Statement declStmt = decl.findAncestor(Statement.class).orElse(null);
            if (declStmt == null) continue;

            List<Statement> statements = block.getStatements();
            int index = statements.indexOf(declStmt);

            if (index == -1) continue;

            String resourceName = var.getNameAsString();

            TryStmt tryStmt = new TryStmt();
            tryStmt.getResources().add(decl.clone());

            BlockStmt tryBlock = new BlockStmt();

            for (int i = index + 1; i < statements.size(); i++) {
                tryBlock.addStatement(statements.get(i));
            }

            tryStmt.setTryBlock(tryBlock);

            // Remove redundant close AFTER try block is built
            removeRedundantCloseCalls(tryStmt, resourceName);

            for (int i = statements.size() - 1; i > index; i--) {
                statements.remove(i);
            }

            statements.set(index, tryStmt);

            return true;
        }

        return false;
    }

    private void removeRedundantCloseCalls(TryStmt tryStmt, String resourceName) {

        tryStmt.getTryBlock().findAll(MethodCallExpr.class).forEach(call -> {

            if (!call.getNameAsString().equals("close"))
                return;

            if (call.getScope().isEmpty())
                return;

            if (!call.getScope().get().toString().equals(resourceName))
                return;

            call.findAncestor(ExpressionStmt.class)
                    .ifPresent(ExpressionStmt::remove);
        });
    }
}