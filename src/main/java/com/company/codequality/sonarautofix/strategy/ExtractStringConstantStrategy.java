package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ExtractStringConstantStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.EXTRACT_STRING_CONSTANT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        try {

            // Find string literal at given line
            Optional<StringLiteralExpr> literalOpt =
                    cu.findAll(StringLiteralExpr.class).stream()
                            .filter(l -> l.getBegin().isPresent()
                                    && l.getBegin().get().line == line)
                            .findFirst();

            if (literalOpt.isEmpty()) {
                return false;
            }

            StringLiteralExpr literal = literalOpt.get();
            String value = literal.getValue();

            // Generate constant name
            String constantName = value
                    .toUpperCase()
                    .replaceAll("[^A-Z0-9]", "_");

            if (constantName.isBlank()) {
                constantName = "EXTRACTED_CONSTANT";
            }

            // Find first class in file
            Optional<ClassOrInterfaceDeclaration> classOpt =
                    cu.findFirst(ClassOrInterfaceDeclaration.class);

            if (classOpt.isEmpty()) {
                return false;
            }

            ClassOrInterfaceDeclaration clazz = classOpt.get();

            // Check if constant already exists (NO STREAMS → SAFE)
            boolean exists = false;

            for (FieldDeclaration field : clazz.getFields()) {
                if (!field.getVariables().isEmpty()) {
                    String fieldName = field.getVariable(0).getNameAsString();
                    if (fieldName.equals(constantName)) {
                        exists = true;
                        break;
                    }
                }
            }

            // Add constant if not present
            if (!exists) {
                clazz.addFieldWithInitializer(
                        "String",
                        constantName,
                        new StringLiteralExpr(value),
                        Modifier.Keyword.PRIVATE,
                        Modifier.Keyword.STATIC,
                        Modifier.Keyword.FINAL
                );
            }

            // Replace literal usage
            literal.replace(new NameExpr(constantName));

            return true;

        } catch (Exception e) {
            System.out.println("⚠ ExtractStringConstant failed at line " + line);
            return false;
        }
    }
}