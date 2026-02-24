package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.company.codequality.sonarautofix.util.LoggerUtil;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.CatchClause;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AddLoggerToEmptyCatchStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.ADD_LOGGER_TO_EMPTY_CATCH;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line)  {

        List<CatchClause> catches = cu.findAll(CatchClause.class);

        for (CatchClause cc : catches) {

            if (!cc.getBegin().isPresent()) continue;

            int startline = cc.getBegin().get().line;

            if (startline != line) continue;

            // Check Already handled
            if (!cc.getBody().getStatements().isEmpty()) {
                return false;
            }

            String exceptionName =
                    cc.getParameter().getNameAsString();

            cc.getBody().addStatement(
                    "log.error(\"Exception occurred\", "
                            + exceptionName + ");"
            );

            LoggerUtil.ensureSlf4jLoggerExists(cu);

            return true;
        }

        return false;
    }
}