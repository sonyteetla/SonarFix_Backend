package com.company.codequality.sonarautofix.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Service
public class VariableRenameService {

    public void renameVariableInProject(String projectPath, String oldName, String newName) {
        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(path -> {
                     boolean[] changed = {false};
                     try {
                         String oldCap = oldName.substring(0, 1).toUpperCase() + oldName.substring(1);
                         String newCap = newName.substring(0, 1).toUpperCase() + newName.substring(1);
                         String oldGetter = "get" + oldCap;
                         String newGetter = "get" + newCap;
                         String oldSetter = "set" + oldCap;
                         String newSetter = "set" + newCap;

                         CompilationUnit cu = StaticJavaParser.parse(path);
                         com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter.setup(cu);

                         cu.findAll(com.github.javaparser.ast.expr.SimpleName.class).forEach(sn -> {
                             String id = sn.getIdentifier();
                             if (id.equals(oldName)) {
                                 sn.setIdentifier(newName);
                                 changed[0] = true;
                             } else if (id.equals(oldGetter)) {
                                 sn.setIdentifier(newGetter);
                                 changed[0] = true;
                             } else if (id.equals(oldSetter)) {
                                 sn.setIdentifier(newSetter);
                                 changed[0] = true;
                             }
                         });

                         if (changed[0]) {
                             Files.writeString(path, com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter.print(cu));
                         }
                     } catch (Exception e) {
                         System.err.println("Failed to parse or modify file: " + path);
                     }
                 });
        } catch (Exception e) {
            throw new RuntimeException("Error traversing project path for renaming variable: " + e.getMessage(), e);
        }
    }
}
