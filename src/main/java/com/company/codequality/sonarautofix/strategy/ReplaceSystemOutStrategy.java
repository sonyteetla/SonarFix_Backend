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
    public boolean apply(CompilationUnit cu, int line) {
        boolean fixedAny = false;

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {

            if (line != -1 && call.getBegin().isPresent()
                    && call.getBegin().get().line != line) {
                continue;
            }

            if (transformIfSystemCall(cu, call)) {
                fixedAny = true;
                if (line != -1) return true;
            }
        }

        return fixedAny;
    }

    private boolean transformIfSystemCall(CompilationUnit cu, MethodCallExpr call) {

        if (call.getScope().isEmpty()) return false;

        if (!(call.getScope().get() instanceof FieldAccessExpr fieldAccess))
            return false;

        if (!(fieldAccess.getScope() instanceof NameExpr nameExpr))
            return false;

        if (!nameExpr.getNameAsString().equals("System"))
            return false;

        String stream = fieldAccess.getNameAsString();
        String method = call.getNameAsString();

        if (!(method.equals("print") || method.equals("println") || method.equals("printf")))
            return false;

        String level = stream.equals("err") ? "error" : "info";

        LoggerUtil.ensureSlf4jLoggerExists(cu);

        if (method.equals("printf")) {
            handlePrintf(call);
        } else if (!call.getArguments().isEmpty()) {

            Expression arg = call.getArgument(0);

            if (arg instanceof BinaryExpr) {
                handleStringConcatenation(call);
            } else {

                // convert println(x) -> log.info("{}", x)
                call.getArguments().clear();
                call.addArgument(new StringLiteralExpr("{}"));
                call.addArgument(arg.clone());
            }
        }

        call.setScope(new NameExpr("log"));
        call.setName(level);

        return true;
    }

    private void handlePrintf(MethodCallExpr call) {

        if (call.getArguments().isEmpty()) return;

        Expression first = call.getArgument(0);

        if (first instanceof StringLiteralExpr literal) {

            String converted =
                    literal.getValue().replaceAll("%[a-zA-Z]", "{}");

            literal.setString(converted);
        }
    }

    private void handleStringConcatenation(MethodCallExpr call) {

        Expression arg = call.getArgument(0);

        BinaryExpr binary = (BinaryExpr) arg;

        List<Expression> parts = new ArrayList<>();
        flattenBinary(binary, parts);

        StringBuilder message = new StringBuilder();
        List<Expression> params = new ArrayList<>();

        for (Expression part : parts) {

            if (part instanceof StringLiteralExpr literal) {
                message.append(literal.getValue());
            } else {
                message.append("{}");
                params.add(part);
            }
        }

        call.getArguments().clear();
        call.addArgument(new StringLiteralExpr(message.toString()));

        params.forEach(call::addArgument);
    }

    private void flattenBinary(Expression expr, List<Expression> parts) {

        if (expr instanceof BinaryExpr binary
                && binary.getOperator() == BinaryExpr.Operator.PLUS) {

            flattenBinary(binary.getLeft(), parts);
            flattenBinary(binary.getRight(), parts);

        } else {
            parts.add(expr);
        }
    }
}