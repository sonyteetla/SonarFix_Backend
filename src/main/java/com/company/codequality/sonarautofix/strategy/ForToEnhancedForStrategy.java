package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

/**
 * java:S1319 — Convert index-based for loops to enhanced for-each loops.
 *
 * Before:
 * for (int i = 0; i < list.size(); i++) { process(list.get(i)); }
 * After:
 * for (var i_item : list) { process(i_item); }
 */
@Component
public class ForToEnhancedForStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.FOR_TO_ENHANCED_FOR;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {
        for (ForStmt forStmt : cu.findAll(ForStmt.class)) {
            if (literalMatch(forStmt, line)) {
                ConversionInfo info = analyze(forStmt);
                if (info == null)
                    continue;

                String indexVar = info.indexVar();
                String collectionName = info.collectionName();
                String elementName = indexVar + "_item";

                Statement newBody = forStmt.getBody().clone();
                newBody = replaceIndexAccess(newBody, indexVar, collectionName, elementName, info.isArray());

                Expression collectionExpr = parseCollectionExpression(collectionName);

                ForEachStmt forEach = new ForEachStmt(
                        new VariableDeclarationExpr(
                                new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "var"),
                                elementName),
                        collectionExpr,
                        newBody);

                forStmt.replace(forEach);
                return true;
            }
        }
        return false;
    }

    private boolean literalMatch(ForStmt forStmt, int line) {
        return forStmt.getRange().map(range -> {
            return (range.begin.line <= line && range.end.line >= line)
                    || (Math.abs(range.begin.line - line) <= 1);
        }).orElse(false);
    }

    private Expression parseCollectionExpression(String name) {
        if (name.contains(".")) {
            String[] parts = name.split("\\.");
            Expression scope = parts[0].equals("this") ? new ThisExpr() : new NameExpr(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                scope = new FieldAccessExpr(scope, parts[i]);
            }
            return scope;
        }
        return new NameExpr(name);
    }

    // ── Analysis ────────────────────────────────────────────────────────────

    private record ConversionInfo(String indexVar, String collectionName, boolean isArray) {
    }

    private ConversionInfo analyze(ForStmt forStmt) {

        // Init: int i = 0
        if (forStmt.getInitialization().size() != 1)
            return null;
        Expression init = forStmt.getInitialization().get(0);
        if (!(init instanceof VariableDeclarationExpr vde))
            return null;
        if (vde.getVariables().size() != 1)
            return null;
        VariableDeclarator var = vde.getVariables().get(0);
        if (var.getInitializer().isEmpty())
            return null;
        if (!(var.getInitializer().get() instanceof IntegerLiteralExpr ile))
            return null;
        if (ile.asNumber().intValue() != 0)
            return null; // must start at 0

        String indexVar = var.getNameAsString();

        // Condition: i < collection.size() or i < array.length
        if (forStmt.getCompare().isEmpty())
            return null;
        Expression cmp = forStmt.getCompare().get();
        if (!(cmp instanceof BinaryExpr be))
            return null;
        if (be.getOperator() != BinaryExpr.Operator.LESS)
            return null;
        if (!(be.getLeft() instanceof NameExpr ni) ||
                !ni.getNameAsString().equals(indexVar))
            return null;

        String collectionName;
        boolean isArray;

        Expression right = be.getRight();
        if (right instanceof MethodCallExpr mce &&
                mce.getNameAsString().equals("size") &&
                mce.getScope().isPresent()) {
            collectionName = mce.getScope().get().toString();
            isArray = false;
        } else if (right instanceof FieldAccessExpr fae &&
                fae.getNameAsString().equals("length")) {
            collectionName = fae.getScope().toString();
            isArray = true;
        } else {
            return null;
        }

        // Update: i++ or ++i
        if (forStmt.getUpdate().size() != 1)
            return null;
        Expression update = forStmt.getUpdate().get(0);
        if (!(update instanceof UnaryExpr ue))
            return null;
        if (ue.getOperator() != UnaryExpr.Operator.POSTFIX_INCREMENT &&
                ue.getOperator() != UnaryExpr.Operator.PREFIX_INCREMENT)
            return null;
        if (!(ue.getExpression() instanceof NameExpr une) ||
                !une.getNameAsString().equals(indexVar))
            return null;

        return new ConversionInfo(indexVar, collectionName, isArray);
    }

    // ── Transform ───────────────────────────────────────────────────────────

    private Statement replaceIndexAccess(Statement body,
            String indexVar,
            String collectionName,
            String elementName,
            boolean isArray) {

        final String searchName = collectionName.startsWith("this.") ? collectionName.substring(5) : collectionName;

        if (isArray) {
            for (ArrayAccessExpr aae : body.findAll(ArrayAccessExpr.class)) {
                String aaeName = aae.getName().toString();
                if (aaeName.startsWith("this."))
                    aaeName = aaeName.substring(5);

                if (aaeName.equals(searchName) &&
                        aae.getIndex() instanceof NameExpr idx &&
                        idx.getNameAsString().equals(indexVar)) {
                    aae.replace(new NameExpr(elementName));
                }
            }
        } else {
            for (MethodCallExpr mce : body.findAll(MethodCallExpr.class)) {
                if (mce.getNameAsString().equals("get") && mce.getScope().isPresent()
                        && mce.getArguments().size() == 1) {
                    String scopeStr = mce.getScope().get().toString();
                    if (scopeStr.startsWith("this."))
                        scopeStr = scopeStr.substring(5);

                    if (scopeStr.equals(searchName) &&
                            mce.getArguments().get(0) instanceof NameExpr idx &&
                            idx.getNameAsString().equals(indexVar)) {
                        mce.replace(new NameExpr(elementName));
                    }
                }
            }
        }
        return body;
    }
}
