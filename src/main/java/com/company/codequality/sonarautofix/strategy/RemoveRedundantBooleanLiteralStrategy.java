package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;

import com.github.javaparser.resolution.types.ResolvedType;
import org.springframework.stereotype.Component;

@Component
public class RemoveRedundantBooleanLiteralStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_REDUNDANT_BOOLEAN;
    }

    @Override
    public boolean apply(CompilationUnit cu, int startLine) {

        // ── Pass 1: x == true / x != false / true == x etc. ──────────────────
        for (BinaryExpr binary : cu.findAll(BinaryExpr.class)) {

            if (!isOnLine(binary, startLine))
                continue;

            if (binary.getOperator() != BinaryExpr.Operator.EQUALS &&
                binary.getOperator() != BinaryExpr.Operator.NOT_EQUALS)
                continue;

            Expression left  = binary.getLeft();
            Expression right = binary.getRight();

            if (left instanceof BooleanLiteralExpr literal) {
                if (isBooleanType(right))
                    return transform(binary, right, literal.getValue());
            }

            if (right instanceof BooleanLiteralExpr literal) {
                if (isBooleanType(left))
                    return transform(binary, left, literal.getValue());
            }
        }

        // ── Pass 2: cond ? x : false / cond ? true : x etc. ──────────────────
        for (ConditionalExpr ternary : cu.findAll(ConditionalExpr.class)) {

            if (!isOnLine(ternary, startLine))
                continue;

            Expression cond  = ternary.getCondition();
            Expression then  = ternary.getThenExpr();
            Expression elseE = ternary.getElseExpr();

            // cond ? x : false  →  cond && x
            if (isFalseLiteral(elseE) && isBooleanType(then)) {
                ternary.replace(
                    new BinaryExpr(
                        cond.clone(),
                        removeRedundantParentheses(then.clone()),
                        BinaryExpr.Operator.AND
                    )
                );
                return true;
            }

            // cond ? true : x  →  cond || x
            if (isTrueLiteral(then) && isBooleanType(elseE)) {
                ternary.replace(
                    new BinaryExpr(
                        cond.clone(),
                        removeRedundantParentheses(elseE.clone()),
                        BinaryExpr.Operator.OR
                    )
                );
                return true;
            }

            // cond ? false : x  →  !cond && x
            if (isFalseLiteral(then) && isBooleanType(elseE)) {
                ternary.replace(
                    new BinaryExpr(
                        negate(cond.clone()),
                        removeRedundantParentheses(elseE.clone()),
                        BinaryExpr.Operator.AND
                    )
                );
                return true;
            }

            // cond ? x : true  →  !cond || x
            if (isTrueLiteral(elseE) && isBooleanType(then)) {
                ternary.replace(
                    new BinaryExpr(
                        negate(cond.clone()),
                        removeRedundantParentheses(then.clone()),
                        BinaryExpr.Operator.OR
                    )
                );
                return true;
            }
        }

        return false;
    }

    // ── Binary transform (Pass 1) ─────────────────────────────────────────────

    private boolean transform(BinaryExpr binary,
                              Expression expr,
                              boolean literalValue) {

        Expression cleaned = removeRedundantParentheses(expr.clone());

        boolean isEquals = binary.getOperator() == BinaryExpr.Operator.EQUALS;

        // == true  →  expr        != true  →  !expr
        // == false →  !expr       != false →  expr
        Expression replacement = (isEquals == literalValue)
                ? cleaned
                : negate(cleaned);

        binary.replace(replacement);
        return true;
    }

    // ── Type checks ───────────────────────────────────────────────────────────

    /**
     * Accepts both primitive boolean and boxed Boolean.
     * Falls back to true on resolution failure so we still attempt the fix
     * rather than silently skipping it.
     */
    private boolean isBooleanType(Expression expr) {
        try {
            ResolvedType type = expr.calculateResolvedType();
            String desc = type.describe();
            return desc.equals("boolean") || desc.equals("java.lang.Boolean");
        } catch (Exception e) {
            // Symbol solver not configured, or unresolvable expression.
            // Allow the fix — worst case the code stays compilable.
            return true;
        }
    }

    // ── Literal helpers ───────────────────────────────────────────────────────

    private boolean isTrueLiteral(Expression e) {
        return e instanceof BooleanLiteralExpr b && b.getValue();
    }

    private boolean isFalseLiteral(Expression e) {
        return e instanceof BooleanLiteralExpr b && !b.getValue();
    }

    // ── AST helpers ───────────────────────────────────────────────────────────

    /**
     * Negates an expression.
     * Avoids double-negation: !!flag → flag
     */
    private Expression negate(Expression expr) {
        if (expr instanceof UnaryExpr unary &&
            unary.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
            return removeRedundantParentheses(unary.getExpression().clone());
        }
        // Wrap in parens only if needed (method calls / name exprs don't need them)
        Expression inner = needsParens(expr)
                ? new EnclosedExpr(expr)
                : expr;
        return new UnaryExpr(inner, UnaryExpr.Operator.LOGICAL_COMPLEMENT);
    }

    /**
     * Strips redundant parentheses: ((x)) → x
     */
    private Expression removeRedundantParentheses(Expression expr) {
        while (expr instanceof EnclosedExpr enclosed) {
            expr = enclosed.getInner();
        }
        return expr;
    }

    /**
     * Wrapping in parens is only necessary for binary / ternary / assign exprs
     * to preserve operator precedence when negating.
     */
    private boolean needsParens(Expression expr) {
        return expr instanceof BinaryExpr
            || expr instanceof ConditionalExpr
            || expr instanceof AssignExpr;
    }

    /**
     * Returns true if the node's source range overlaps the target line.
     * Line == -1 means "process everything" (batch / full-file mode).
     */
    private boolean isOnLine(Node node, int startLine) {
        if (startLine == -1)
            return true;
        return node.getRange()
                .map(r -> r.begin.line <= startLine && r.end.line >= startLine)
                .orElse(false);
    }
}