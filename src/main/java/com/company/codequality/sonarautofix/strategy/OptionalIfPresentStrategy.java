package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.Range;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.resolution.types.ResolvedType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OptionalIfPresentStrategy implements FixStrategy {


@Override
public FixType getFixType() {
    return FixType.OPTIONAL_IF_PRESENT;
}

@Override
public boolean apply(CompilationUnit cu, int startLine) {

    for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {

        Optional<Range> rangeOpt = call.getRange();
        if (rangeOpt.isEmpty())
            continue;

        Range range = rangeOpt.get();

        if (startLine != -1 &&
                (startLine < range.begin.line || startLine > range.end.line))
            continue;

        if (!call.getNameAsString().equals("get"))
            continue;

        if (call.getScope().isEmpty())
            continue;

        Expression scope = call.getScope().get();

        if (!isJavaOptional(scope))
            continue;

        if (isPrimitiveOptional(scope))
            continue;

        if (isAlreadySafeChain(call))
            continue;

        if (isGuardedByDominatingCheck(call, scope))
            continue;

        MethodCallExpr replacement =
                new MethodCallExpr(scope.clone(), "orElseThrow");

        call.replace(replacement);
        return true;
    }

    return false;
}

// ================= TYPE CHECK =================

private boolean isJavaOptional(Expression scope) {
    try {

        ResolvedType type = scope.calculateResolvedType();

        if (!type.isReferenceType())
            return false;

        String qName = type.asReferenceType().getQualifiedName();

        return "java.util.Optional".equals(qName);

    } catch (Exception e) {
        return false;
    }
}

private boolean isPrimitiveOptional(Expression scope) {
    try {

        ResolvedType type = scope.calculateResolvedType();

        if (!type.isReferenceType())
            return false;

        String qName = type.asReferenceType().getQualifiedName();

        return qName.equals("java.util.OptionalInt")
                || qName.equals("java.util.OptionalLong")
                || qName.equals("java.util.OptionalDouble");

    } catch (Exception e) {
        return false;
    }
}

// ================= SAFE CONTEXT CHECK =================

private boolean isAlreadySafeChain(MethodCallExpr call) {

    Optional<Node> parent = call.getParentNode();

    if (parent.isEmpty())
        return false;

    if (parent.get() instanceof MethodCallExpr parentCall) {

        String name = parentCall.getNameAsString();

        return name.equals("orElse")
                || name.equals("orElseThrow")
                || name.equals("ifPresent")
                || name.equals("map")
                || name.equals("flatMap")
                || name.equals("filter");
    }

    return false;
}

// ================= DOMINATING GUARD DETECTION =================

private boolean isGuardedByDominatingCheck(MethodCallExpr call,
                                           Expression scope) {

    Optional<BlockStmt> blockOpt =
            call.findAncestor(BlockStmt.class);

    if (blockOpt.isEmpty())
        return false;

    BlockStmt block = blockOpt.get();

    List<Statement> statements = block.getStatements();

    Statement currentStmt =
            call.findAncestor(Statement.class).orElse(null);

    if (currentStmt == null)
        return false;

    int index = statements.indexOf(currentStmt);

    if (index == -1)
        return false;

    for (int i = 0; i < index; i++) {

        Statement stmt = statements.get(i);

        if (!(stmt instanceof IfStmt ifStmt))
            continue;

        if (!containsIsPresentCheck(ifStmt.getCondition(), scope))
            continue;

        if (isNegativeGuardWithExit(ifStmt))
            return true;

        if (callIsInsideThen(call, ifStmt))
            return true;
    }

    return false;
}

private boolean containsIsPresentCheck(Expression condition,
                                       Expression scope) {

    return condition.findAll(MethodCallExpr.class)
            .stream()
            .anyMatch(m ->
                    m.getNameAsString().equals("isPresent")
                            && m.getScope().isPresent()
                            && m.getScope().get().toString()
                            .equals(scope.toString()));
}

private boolean isNegativeGuardWithExit(IfStmt ifStmt) {

    Expression cond = ifStmt.getCondition();

    if (cond instanceof UnaryExpr unary
            && unary.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {

        Expression inner = unary.getExpression();

        if (inner instanceof MethodCallExpr m
                && m.getNameAsString().equals("isPresent")) {

            Statement thenStmt = ifStmt.getThenStmt();

            if (thenStmt instanceof ReturnStmt
                    || thenStmt instanceof ThrowStmt) {
                return true;
            }
        }
    }

    return false;
}

private boolean callIsInsideThen(MethodCallExpr call,
                                 IfStmt ifStmt) {

    return ifStmt.getThenStmt()
            .findAll(MethodCallExpr.class)
            .stream()
            .anyMatch(c -> c == call);
}


}
