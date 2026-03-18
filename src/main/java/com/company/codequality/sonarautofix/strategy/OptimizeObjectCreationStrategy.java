package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OptimizeObjectCreationStrategy implements FixStrategy {


@Override
public FixType getFixType() {
    return FixType.OPTIMIZE_OBJECT_CREATION;
}

@Override
public boolean apply(CompilationUnit cu, int line) {

    boolean fixed = false;

    for (ObjectCreationExpr obj : cu.findAll(ObjectCreationExpr.class)) {

        if (!shouldProcess(obj, line))
            continue;

        if (!isInsideLoop(obj))
            continue;

        try {

            // Case 1: Replace new String("text") → "text"
            if ("String".equals(obj.getType().getNameAsString())
                    && obj.getArguments().size() == 1
                    && obj.getArgument(0) instanceof StringLiteralExpr literal) {

                obj.replace(new StringLiteralExpr(literal.getValue()));
                fixed = true;
                continue;
            }

            // Avoid breaking logic if loop variable is used
            if (usesLoopVariable(obj))
                continue;

            Optional<VariableDeclarator> varOpt =
                    obj.findAncestor(VariableDeclarator.class);

            if (varOpt.isEmpty())
                continue;

            VariableDeclarator var = varOpt.get();

            Optional<Statement> stmtOpt =
                    var.findAncestor(Statement.class);

            if (stmtOpt.isEmpty())
                continue;

            Statement declarationStmt = stmtOpt.get();

            // Detect the loop containing the object creation
            Optional<Node> loopOpt =
                    obj.findAncestor(ForStmt.class).map(n -> (Node) n)
                    .or(() -> obj.findAncestor(WhileStmt.class).map(n -> (Node) n))
                    .or(() -> obj.findAncestor(DoStmt.class).map(n -> (Node) n));

            if (loopOpt.isEmpty())
                continue;

            Node loop = loopOpt.get();

            Optional<BlockStmt> parentBlock =
                    loop.findAncestor(BlockStmt.class);

            if (parentBlock.isEmpty())
                continue;

            BlockStmt block = parentBlock.get();

            int index = block.getStatements().indexOf(loop);

            if (index == -1)
                continue;

            // Insert declaration before loop
            block.addStatement(index, declarationStmt.clone());

            // Remove initializer inside loop
            var.removeInitializer();

            fixed = true;

        } catch (Exception ignored) {}
    }

    return fixed;
}

private boolean shouldProcess(ObjectCreationExpr obj, int line) {

    if (line == -1)
        return true;

    if (obj.getBegin().isEmpty())
        return false;

    return obj.getBegin().get().line == line;
}

private boolean isInsideLoop(ObjectCreationExpr obj) {

    return obj.findAncestor(ForStmt.class).isPresent()
            || obj.findAncestor(WhileStmt.class).isPresent()
            || obj.findAncestor(DoStmt.class).isPresent();
}

private boolean usesLoopVariable(ObjectCreationExpr obj) {

    Optional<ForStmt> forLoop = obj.findAncestor(ForStmt.class);

    if (forLoop.isEmpty())
        return false;

    ForStmt loop = forLoop.get();

    if (loop.getInitialization().isEmpty())
        return false;

    String loopVar = loop.getInitialization().get(0)
            .toString()
            .replaceAll(".*\\s", "")
            .replaceAll("=.*", "")
            .trim();

    return obj.toString().contains(loopVar);
}


}
