package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.Modifier;
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

            Optional<StringLiteralExpr> literalOpt = cu.findAll(StringLiteralExpr.class).stream()
                    .filter(l -> l.getBegin().isPresent()
                            && l.getBegin().get().line == line)
                    .findFirst();

            if (literalOpt.isEmpty()) {
                return false;
            }

            StringLiteralExpr literal = literalOpt.get();
            String value = literal.getValue();

            String generatedName = value
                    .toUpperCase()
                    .replaceAll("[^A-Z0-9]", "_");

            if (generatedName.isBlank()) {
                generatedName = "EXTRACTED_CONSTANT";
            }

            final String constantName = generatedName;

            // Find first class only
            Optional<ClassOrInterfaceDeclaration> classOpt = cu.findFirst(ClassOrInterfaceDeclaration.class);

            if (classOpt.isEmpty()) {
                return false; // Not a class file
            }

            ClassOrInterfaceDeclaration clazz = classOpt.get();

            // Avoid duplicate constant creation
            boolean alreadyExists = clazz.getFields().stream()
                    .anyMatch(f -> f.getVariable(0).getNameAsString()
                            .equals(constantName));

            if (alreadyExists) {
                literal.replace(new NameExpr(constantName));
                return true;
            }

            FieldDeclaration field = clazz.addFieldWithInitializer(
                    "String",
                    constantName,
                    new StringLiteralExpr(value),
                    Modifier.Keyword.PRIVATE,
                    Modifier.Keyword.STATIC,
                    Modifier.Keyword.FINAL);

            // Replace ALL duplicate literals in the class EXCEPT the one in the constant's
            // initializer
            cu.findAll(com.github.javaparser.ast.expr.StringLiteralExpr.class).stream()
                    .filter(l -> l.getValue().equals(value))
                    .filter(l -> !l.findAncestor(com.github.javaparser.ast.body.FieldDeclaration.class)
                            .map(f -> f.equals(field)).orElse(false))
                    .forEach(l -> l.replace(new com.github.javaparser.ast.expr.NameExpr(constantName)));

            return true;

        } catch (Exception e) {
            System.out.println("⚠ ExtractStringConstant failed at line " + line);
            return false;
        }
    }
}