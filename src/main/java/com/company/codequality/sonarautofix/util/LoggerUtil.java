package com.company.codequality.sonarautofix.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;

import java.util.List;

public class LoggerUtil {

    public static void ensureSlf4jLoggerExists(CompilationUnit cu) {

        ensureImport(cu, "org.slf4j.Logger");
        ensureImport(cu, "org.slf4j.LoggerFactory");

        List<ClassOrInterfaceDeclaration> classes =
                cu.findAll(ClassOrInterfaceDeclaration.class);

        if (classes.isEmpty()) {
            return;
        }

        // Only modify first top-level class
        ClassOrInterfaceDeclaration clazz = classes.get(0);

        if (loggerAlreadyExists(clazz)) {
            return;
        }

        FieldDeclaration loggerField =
                StaticJavaParser.parseBodyDeclaration(
                        "private static final Logger log = " +
                        "LoggerFactory.getLogger(" +
                        clazz.getNameAsString() + ".class);"
                ).asFieldDeclaration();

        clazz.getMembers().addFirst(loggerField);
    }

    private static boolean loggerAlreadyExists(ClassOrInterfaceDeclaration clazz) {

        for (FieldDeclaration field : clazz.getFields()) {

            String type = field.getElementType().asString();

            if ("Logger".equals(type)
                    || "org.slf4j.Logger".equals(type)) {

                return true;
            }
        }

        return false;
    }

    private static void ensureImport(CompilationUnit cu, String importName) {

        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(importName)) {
                return;
            }
        }

        cu.addImport(importName);
    }
}