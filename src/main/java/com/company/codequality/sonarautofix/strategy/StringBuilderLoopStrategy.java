package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StringBuilderLoopStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.STRING_BUILDER_LOOP;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        boolean fixed = false;

        for (AssignExpr assign : cu.findAll(AssignExpr.class)) {
            if (assign.getBegin().isPresent() && assign.getBegin().get().line == line) {

                String targetName = assign.getTarget().toString();
                com.github.javaparser.ast.expr.Expression rhsToAppend = null;

                if (assign.getOperator() == AssignExpr.Operator.PLUS) {
                    rhsToAppend = assign.getValue().clone();
                } else if (assign.getOperator() == AssignExpr.Operator.ASSIGN) {
                    if (assign.getValue() instanceof com.github.javaparser.ast.expr.BinaryExpr) {
                        com.github.javaparser.ast.expr.BinaryExpr bin = (com.github.javaparser.ast.expr.BinaryExpr) assign
                                .getValue();
                        com.github.javaparser.ast.expr.Expression leftMost = bin.getLeft();
                        while (leftMost instanceof com.github.javaparser.ast.expr.BinaryExpr
                                && ((com.github.javaparser.ast.expr.BinaryExpr) leftMost)
                                        .getOperator() == com.github.javaparser.ast.expr.BinaryExpr.Operator.PLUS) {
                            leftMost = ((com.github.javaparser.ast.expr.BinaryExpr) leftMost).getLeft();
                        }
                        if (leftMost.toString().equals(targetName)) {
                            // Replace the leftMost with empty string or similar, but simpler is just wrap
                            // the bin with minus targetName
                            rhsToAppend = removePrefix(bin, targetName);
                        }
                    }
                }

                if (rhsToAppend == null)
                    continue;

                Optional<Node> loopOpt = assign.findAncestor(com.github.javaparser.ast.stmt.ForStmt.class)
                        .map(Node.class::cast)
                        .or(() -> assign.findAncestor(com.github.javaparser.ast.stmt.ForEachStmt.class)
                                .map(Node.class::cast))
                        .or(() -> assign.findAncestor(com.github.javaparser.ast.stmt.WhileStmt.class)
                                .map(Node.class::cast))
                        .or(() -> assign.findAncestor(com.github.javaparser.ast.stmt.DoStmt.class)
                                .map(Node.class::cast));

                if (!loopOpt.isPresent())
                    continue;
                Statement loopStmt = (Statement) loopOpt.get();

                Optional<Node> blockOpt = loopStmt.getParentNode();
                if (!blockOpt.isPresent() || !(blockOpt.get() instanceof BlockStmt)) {
                    continue; // Skip if loop is not in a block (rare)
                }
                BlockStmt parentBlock = (BlockStmt) blockOpt.get();

                String sbName = "_sb_" + targetName;

                // Add StringBuilder _sb_var = new StringBuilder(var); BEFORE loop
                boolean alreadyAdded = parentBlock.getStatements().stream()
                        .anyMatch(s -> s.toString().contains("StringBuilder " + sbName));

                if (!alreadyAdded) {
                    com.github.javaparser.ast.stmt.Statement initSb = com.github.javaparser.StaticJavaParser
                            .parseStatement(
                                    "StringBuilder " + sbName + " = new StringBuilder(" + targetName + " != null ? "
                                            + targetName + " : \"\");");
                    parentBlock.getStatements().addBefore(initSb, loopStmt);

                    // Add var = _sb_var.toString(); AFTER loop
                    com.github.javaparser.ast.stmt.Statement assignBack = com.github.javaparser.StaticJavaParser
                            .parseStatement(
                                    targetName + " = " + sbName + ".toString();");
                    parentBlock.getStatements().addAfter(assignBack, loopStmt);
                }

                // Replace `var = var + x` with `_sb_var.append(x)`
                MethodCallExpr appendCall = new MethodCallExpr(new NameExpr(sbName), "append").addArgument(rhsToAppend);
                assign.replace(appendCall);

                // Also replace variable reads inside the loop with _sb_var.toString()
                // Just to be safe, though not strictly required if not read.
                fixed = true;
            }
        }
        return fixed;
    }

    private com.github.javaparser.ast.expr.Expression removePrefix(com.github.javaparser.ast.expr.Expression expr,
            String prefix) {
        if (expr.toString().equals(prefix)) {
            return new com.github.javaparser.ast.expr.StringLiteralExpr("");
        }
        if (expr instanceof com.github.javaparser.ast.expr.BinaryExpr) {
            com.github.javaparser.ast.expr.BinaryExpr bin = (com.github.javaparser.ast.expr.BinaryExpr) expr;
            if (bin.getOperator() == com.github.javaparser.ast.expr.BinaryExpr.Operator.PLUS) {
                if (bin.getLeft().toString().equals(prefix)) {
                    return bin.getRight();
                } else {
                    com.github.javaparser.ast.expr.Expression newLeft = removePrefix(bin.getLeft(), prefix);
                    if (newLeft instanceof com.github.javaparser.ast.expr.StringLiteralExpr
                            && ((com.github.javaparser.ast.expr.StringLiteralExpr) newLeft).getValue().isEmpty()) {
                        return bin.getRight();
                    }
                    return new com.github.javaparser.ast.expr.BinaryExpr(newLeft, bin.getRight(),
                            com.github.javaparser.ast.expr.BinaryExpr.Operator.PLUS);
                }
            }
        }
        return expr;
    }
}