package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.company.codequality.sonarautofix.util.LoggerUtil;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ReplaceSystemOutStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REPLACE_SYSTEM_OUT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line)  {

        List<MethodCallExpr> calls = cu.findAll(MethodCallExpr.class);

        // First pass: exact line match
        for (MethodCallExpr call : calls) {

            if (!call.getBegin().isPresent()) continue;
            if (call.getBegin().get().line != line) continue;

            if (transformIfSystemCall(cu, call)) {
                return true;
            }
        }

        //  Second pass: fallback structural match
        for (MethodCallExpr call : calls) {

            if (transformIfSystemCall(cu, call)) {
                return true;
            }
        }

        return false;
    }

    private boolean transformIfSystemCall(CompilationUnit cu,
                                          MethodCallExpr call) {

        if (!call.getScope().isPresent()) return false;

        // Already converted? Treat as success
        if (call.getScope().get().toString().equals("log")) {
            return true;
        }

        if (!(call.getScope().get() instanceof FieldAccessExpr)) return false;

        FieldAccessExpr fieldAccess =
                (FieldAccessExpr) call.getScope().get();

        if (!(fieldAccess.getScope() instanceof NameExpr)) return false;

        String rootName =
                ((NameExpr) fieldAccess.getScope()).getNameAsString();

        if (!"System".equals(rootName)) return false;

        String stream = fieldAccess.getNameAsString();
        String method = call.getNameAsString();

        if (!("print".equals(method)
                || "println".equals(method)
                || "printf".equals(method))) {
            return false;
        }

        String logLevel;

        if ("out".equals(stream)) {
            logLevel = "info";
        } else if ("err".equals(stream)) {
            logLevel = "error";
        } else {
            return false;
        }

        //  Handle printf conversion
        if ("printf".equals(method)) {
            handlePrintf(call);
        }

        //  Handle string concatenation
        else if (!call.getArguments().isEmpty()) {
            handleStringConcatenation(call);
        }

        //  Replace with log
        call.setScope(new NameExpr("log"));
        call.setName(logLevel);

        LoggerUtil.ensureSlf4jLoggerExists(cu);

        return true;
    }

    // Converts printf formatting to {}
    private void handlePrintf(MethodCallExpr call) {

        if (call.getArguments().isEmpty()) return;

        Expression firstArg = call.getArgument(0);

        if (firstArg instanceof StringLiteralExpr) {

            StringLiteralExpr literal =
                    (StringLiteralExpr) firstArg;

            String original = literal.getValue();

            String converted = original.replaceAll(
                    "%(\\d+\\$)?[-+# 0,(]*\\d*(\\.\\d+)?[a-zA-Z]",
                    "{}"
            );

            literal.setString(converted);
        }
    }

    // Converts concatenation to {}
    private void handleStringConcatenation(MethodCallExpr call) {

        Expression arg = call.getArgument(0);

        if (!(arg instanceof BinaryExpr)) return;

        BinaryExpr binary = (BinaryExpr) arg;

        List<Expression> parts = new ArrayList<>();
        flattenBinary(binary, parts);

        StringBuilder message = new StringBuilder();
        List<Expression> newArgs = new ArrayList<>();

        for (Expression part : parts) {

            if (part instanceof StringLiteralExpr) {
                message.append(((StringLiteralExpr) part).getValue());
            } else {
                message.append("{}");
                newArgs.add(part);
            }
        }

        call.getArguments().clear();
        call.addArgument(new StringLiteralExpr(message.toString()));

        for (Expression expr : newArgs) {
            call.addArgument(expr);
        }
    }

    private void flattenBinary(Expression expr,
                               List<Expression> parts) {

        if (expr instanceof BinaryExpr
                && ((BinaryExpr) expr).getOperator()
                == BinaryExpr.Operator.PLUS) {

            BinaryExpr binary = (BinaryExpr) expr;
            flattenBinary(binary.getLeft(), parts);
            flattenBinary(binary.getRight(), parts);

        } else {
            parts.add(expr);
        }
    }
}