package com.company.codequality.sonarautofix.strategy;

import com.company.codequality.sonarautofix.model.FixType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ExtractStringConstantStrategy implements FixStrategy {

    @Override
    public FixType getFixType() {
        return FixType.EXTRACT_STRING_CONSTANT;
    }

    @Override
    public boolean apply(CompilationUnit cu, int line) {

        try {

            List<StringLiteralExpr> literals = cu.findAll(StringLiteralExpr.class);

            StringLiteralExpr target = null;

            for (StringLiteralExpr lit : literals) {

                if (lit.getBegin().isPresent()) {

                    int literalLine = lit.getBegin().get().line;

                    // tolerate small line offset from Sonar
                    if (Math.abs(literalLine - line) <= 1) {
                        target = lit;
                        break;
                    }
                }
            }

            if (target == null) {
                return false;
            }

            String value = target.getValue();

            Optional<ClassOrInterfaceDeclaration> classOpt =
                    cu.findFirst(ClassOrInterfaceDeclaration.class);

            if (classOpt.isEmpty()) {
                return false;
            }

            ClassOrInterfaceDeclaration clazz = classOpt.get();

            String constantName = generateConstantName(value);

            // check existing constant
            for (FieldDeclaration field : clazz.getFields()) {

                if (!field.getVariables().isEmpty()) {

                    String existing = field.getVariable(0).getNameAsString();

                    if (existing.equals(constantName)) {

                        replaceAllOccurrences(clazz, value, constantName);
                        return true;
                    }
                }
            }

            // create constant field
            FieldDeclaration constant =
                    clazz.addFieldWithInitializer(
                            "String",
                            constantName,
                            new StringLiteralExpr(value),
                            Modifier.Keyword.PRIVATE,
                            Modifier.Keyword.STATIC,
                            Modifier.Keyword.FINAL
                    );

            // move constant to top
            clazz.getMembers().remove(constant);
            clazz.getMembers().addFirst(constant);

            // replace all occurrences
            replaceAllOccurrences(clazz, value, constantName);

            return true;

        } catch (Exception e) {

            System.out.println("⚠ ExtractStringConstant failed at line " + line);
            e.printStackTrace();
            return false;
        }
    }

    private void replaceAllOccurrences(ClassOrInterfaceDeclaration clazz,
            String value,
            String constantName) {

List<StringLiteralExpr> literals = clazz.findAll(StringLiteralExpr.class);

for (StringLiteralExpr lit : literals) {

if (!lit.getValue().equals(value)) {
continue;
}

Optional<Node> parent = lit.getParentNode();

if (parent.isEmpty()) {
continue;
}

Node p = parent.get();

// skip annotation values
if (p instanceof AnnotationExpr) {
continue;
}

// skip constant initializer
if (p.getParentNode().isPresent() &&
p.getParentNode().get() instanceof FieldDeclaration) {

FieldDeclaration field = (FieldDeclaration) p.getParentNode().get();

if (field.isStatic() && field.isFinal()) {
continue;
}
}
if (lit.findAncestor(FieldDeclaration.class)
	       .map(f -> f.getVariable(0).getNameAsString().equals(constantName))
	       .orElse(false)) {
	    continue;
	}

lit.replace(new NameExpr(constantName));
}
}
    
    private String generateConstantName(String value) {

        String name = value
                .trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_");

        name = name.replaceAll("^_+", "");
        name = name.replaceAll("_+$", "");

        if (name.isBlank()) {
            name = "EXTRACTED_CONSTANT";
        }

        if (!Character.isLetter(name.charAt(0))) {
            name = "CONST_" + name;
        }

        return name;
    }
}