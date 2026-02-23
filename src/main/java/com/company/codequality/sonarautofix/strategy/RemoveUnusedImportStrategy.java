package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RemoveUnusedImportStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.REMOVE_UNUSED_IMPORT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int startLine) throws Exception {

        List<ImportDeclaration> imports = cu.getImports();

        for (ImportDeclaration imp : imports) {

            if (!imp.getBegin().isPresent()) continue;

            int line = imp.getBegin().get().line;

            if (line == startLine) {

                imp.remove();
                return true;
            }
        }

        return false;
    }
}