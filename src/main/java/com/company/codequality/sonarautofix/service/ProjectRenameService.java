package com.company.codequality.sonarautofix.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProjectRenameService {

    // ================= VARIABLE RENAME =================
    public void renameVariableInProject(String projectPath, String oldName, String newName) {
        Set<String> usageRows = new LinkedHashSet<>();
        usageRows.add("OldName,NewName,UsedInFile,LineNumber,EntityType,UsageType");

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
                         LexicalPreservingPrinter.setup(cu);

                         cu.findAll(SimpleName.class).forEach(sn -> {
                        	    String id = sn.getIdentifier();

                        	    if (id.equals(oldName)) {
                        	        int line = sn.getBegin().map(p -> p.line).orElse(-1);

                        	        usageRows.add(oldName + "," + newName + "," +
                        	                path.getFileName() + "," + line + ",VARIABLE, VARIABLE_USAGE");

                        	        sn.setIdentifier(newName);
                        	        changed[0] = true;
                        	    }

                        	    else if (id.equals(oldGetter)) {
                        	        int line = sn.getBegin().map(p -> p.line).orElse(-1);

                        	        usageRows.add(oldGetter + "," + newGetter + "," +
                        	                path.getFileName() + "," + line + ",VARIABLE, GETTER");

                        	        sn.setIdentifier(newGetter);
                        	        changed[0] = true;
                        	    }

                        	    else if (id.equals(oldSetter)) {
                        	        int line = sn.getBegin().map(p -> p.line).orElse(-1);

                        	        usageRows.add(oldSetter + "," + newSetter + "," +
                        	                path.getFileName() + "," + line + ",VARIABLE, SETTER");

                        	        sn.setIdentifier(newSetter);
                        	        changed[0] = true;
                        	    }
                        	});

                         if (changed[0]) {
                             Files.writeString(path, LexicalPreservingPrinter.print(cu));
                         }
                     } catch (Exception e) {
                         System.err.println("Failed to parse or modify file: " + path);
                     }
                 });
        } catch (Exception e) {
            throw new RuntimeException("Error traversing project path for renaming variable: " + e.getMessage(), e);
        }
    


        // Write CSV — append if exists
        writeUsageCSV(projectPath, usageRows,"variable");
    }

    // ================= METHOD RENAME =================
    public void renameMethodInProject(String projectPath, String oldName, String newName) {
        Set<String> usageRows = new LinkedHashSet<>();
        usageRows.add("OldName,NewName,UsedInFile,LineNumber,EntityType,UsageType");

        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(path -> {
                     try {
                         CompilationUnit cu = StaticJavaParser.parse(path);
                         LexicalPreservingPrinter.setup(cu);

                         cu.findAll(MethodDeclaration.class).forEach(md -> {
                        	    if (md.getNameAsString().equals(oldName)) {

                        	        int line = md.getBegin().map(p -> p.line).orElse(-1);

                        	        usageRows.add(oldName + "," + newName + "," +
                        	                path.getFileName() + "," + line + ",METHOD,METHOD_DECL");

                        	        md.setName(newName);
                        	    }
                        	});

                        	cu.findAll(MethodCallExpr.class).forEach(mc -> {
                        	    if (mc.getNameAsString().equals(oldName)) {

                        	        int line = mc.getBegin().map(p -> p.line).orElse(-1);

                        	        usageRows.add(oldName + "," + newName + "," +
                        	                path.getFileName() + "," + line + ",METHOD,METHOD_CALL");

                        	        mc.setName(newName);
                        	    }
                        	});

                        	cu.findAll(MethodReferenceExpr.class).forEach(mr -> {
                        	    if (mr.getIdentifier().equals(oldName)) {

                        	        int line = mr.getBegin().map(p -> p.line).orElse(-1);

                        	        usageRows.add(oldName + "," + newName + "," +
                        	                path.getFileName() + "," + line + ",METHOD,METHOD_REF");

                        	        mr.setIdentifier(newName);
                        	    }
                        	});

                         Files.writeString(path, LexicalPreservingPrinter.print(cu));

                     } catch (Exception e) {
                         System.err.println("Error: " + path);
                     }
                 });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        // Write CSV — append if exists
        writeUsageCSV(projectPath, usageRows,"method");
    }

    // ================= CLASS RENAME =================
    public boolean renameClassInProject(String projectPath, String oldName, String newName) {
        try {
            Path root = Paths.get(projectPath);

            String oldSimple = extractSimpleName(oldName);
            String newSimple = extractSimpleName(newName);

            if (!isRenameSafe(root, oldSimple, newSimple)) {
                return false;
            }

            List<Path> files = Files.walk(root)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            Path classFile = null;

            // ================= PASS 1: CLASS DECLARATION + CONSTRUCTOR =================
            for (Path file : files) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(file);
                    LexicalPreservingPrinter.setup(cu);
                    boolean changed = false;

                    for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        if (cls.getNameAsString().equals(oldSimple)) {
                            cls.setName(newSimple);
                            classFile = file;
                            changed = true;
                        }
                    }
                    for (ConstructorDeclaration cons : cu.findAll(ConstructorDeclaration.class)) {
                        if (cons.getNameAsString().equals(oldSimple)) {
                            cons.setName(newSimple);
                            changed = true;
                        }
                    }

                    if (changed) {
                        Files.writeString(file, LexicalPreservingPrinter.print(cu));
                    }
                } catch (Exception e) {
                    System.out.println("Skipping (pass1): " + file);
                }
            }

            if (classFile == null) {
                System.out.println("❌ Class not found: " + oldSimple);
                return false;
            }

         // ================= PASS 2: ALL USAGES ACROSS ALL FILES =================
            // KEY FIX: Re-walk AFTER pass 1 so the renamed .java file is included.
            // Also catches files that were previously renamed in earlier sessions.
            List<Path> filesForPass2 = Files.walk(root)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            Set<String> usageRows = new LinkedHashSet<>();
            usageRows.add("OldName,NewName,UsedInFile,LineNumber,EntityType,UsageType");

            for (Path file : filesForPass2) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(file);
                    LexicalPreservingPrinter.setup(cu);
                    boolean[] changed = {false};

                    // ✅ FIX: Extract simple name from type string before comparing.
                    // field.getElementType().asString() can return "com.x.UserService"
                    // — we only want "UserService" for comparison.

                    // FIELD types: private final UserService userService;
                    for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
                        // ✅ Use extractSimpleName() to handle fully-qualified type strings
                        String typeSimple = extractSimpleName(field.getElementType().asString());
                        if (typeSimple.equals(oldSimple)) {
                            int line = field.getBegin().map(p -> p.line).orElse(-1);
                            usageRows.add(oldSimple + "," + newSimple + "," + file.getFileName() + "," + line + ",CLASS, FIELD");
                            field.setAllTypes(new ClassOrInterfaceType(null, newSimple));
                            changed[0] = true;
                        }
                    }

                    // PARAMETER types: public OrderService(UserService userService)
                    for (Parameter param : cu.findAll(Parameter.class)) {
                        // ✅ Use extractSimpleName() — same reason as above
                        String typeSimple = extractSimpleName(param.getTypeAsString());
                        if (typeSimple.equals(oldSimple)) {
                            int line = param.getBegin().map(p -> p.line).orElse(-1);
                            usageRows.add(oldSimple + "," + newSimple + "," + file.getFileName() + "," + line + ",CLASS, PARAMETER");
                            param.setType(new ClassOrInterfaceType(null, newSimple));
                            changed[0] = true;
                        }
                    }
                    
             
                    // e.g., UserService temp = new Wonder();
                    for (com.github.javaparser.ast.expr.VariableDeclarationExpr varDecl :
                            cu.findAll(com.github.javaparser.ast.expr.VariableDeclarationExpr.class)) {
                        String typeSimple = extractSimpleName(varDecl.getElementType().asString());
                        if (typeSimple.equals(oldSimple)) {
                            int line = varDecl.getBegin().map(p -> p.line).orElse(-1);
                            usageRows.add(oldSimple + "," + newSimple + "," + file.getFileName() + "," + line + ",CLASS, LOCAL_VAR");
                            varDecl.setAllTypes(new ClassOrInterfaceType(null, newSimple));
                            changed[0] = true;
                        }
                    }

                    // METHOD return types: public UserService getService()
                    for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                        // ✅ Use extractSimpleName()
                        String typeSimple = extractSimpleName(method.getTypeAsString());
                        if (typeSimple.equals(oldSimple)) {
                            int line = method.getBegin().map(p -> p.line).orElse(-1);
                            usageRows.add(oldSimple + "," + newSimple + "," + file.getFileName() + "," + line + ",CLASS, RETURN_TYPES");
                            method.setType(new ClassOrInterfaceType(null, newSimple));
                            changed[0] = true;
                        }
                    }

                    // GENERIC type references: List<UserService>, casts, local vars
                    for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                        if (type.getNameAsString().equals(oldSimple)) {
                            int line = type.getBegin().map(p -> p.line).orElse(-1);
                            usageRows.add(oldSimple + "," + newSimple + "," + file.getFileName() + "," + line + ",CLASS, TYPE_REFERENCE");
                            type.setName(newSimple);
                            changed[0] = true;
                        }
                    }

                    // OBJECT CREATION: new UserService(...)
                    for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
                        if (expr.getType().getNameAsString().equals(oldSimple)) {
                            int line = expr.getBegin().map(p -> p.line).orElse(-1);
                            usageRows.add(oldSimple + "," + newSimple + "," + file.getFileName() + "," + line + ",CLASS, OBJECT_CREATION");
                            expr.setType(newSimple);
                            changed[0] = true;
                        }
                    }

                    // EXTENDS / IMPLEMENTS
                    for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        cls.getExtendedTypes().forEach(ext -> {
                            if (ext.getNameAsString().equals(oldSimple)) {
                                int line = ext.getBegin().map(p -> p.line).orElse(-1);
                                usageRows.add(oldSimple + "," + newSimple + "," + file.getFileName() + "," + line + ",CLASS, EXTENDS");
                                ext.setName(newSimple);
                                changed[0] = true;
                            }
                        });
                        cls.getImplementedTypes().forEach(impl -> {
                            if (impl.getNameAsString().equals(oldSimple)) {
                                int line = impl.getBegin().map(p -> p.line).orElse(-1);
                                usageRows.add(oldSimple + "," + newSimple + "," + file.getFileName() + "," + line + ",CLASS, IMPLEMENTS");
                                impl.setName(newSimple);
                                changed[0] = true;
                            }
                        });
                    }

                    // ANNOTATIONS: @UserService
                    for (AnnotationExpr ann : cu.findAll(AnnotationExpr.class)) {
                        if (ann.getNameAsString().equals(oldSimple)) {
                            int line = ann.getBegin().map(p -> p.line).orElse(-1);
                            usageRows.add(oldSimple + "," + newSimple + "," +
                            	    file.getFileName() + "," + line + ",CLASS,ANNOTATION");
                            ann.setName(newSimple);
                            changed[0] = true;
                        }
                    }

                    // IMPORTS: import com.example.UserService;
                    cu.getImports().forEach(im -> {
                        String importedSimple = extractSimpleName(im.getNameAsString());
                        if (importedSimple.equals(oldSimple)) {
                            int line = im.getBegin().map(p -> p.line).orElse(-1);
                            usageRows.add(oldSimple + "," + newSimple + "," +
                            	    file.getFileName() + "," + line + ",CLASS,IMPORT");
                            // ✅ FIX: Replace only the simple class name at the end, not substring anywhere
                            String oldImport = im.getNameAsString();
                            String newImport = oldImport.substring(0, oldImport.lastIndexOf('.') + 1) + newSimple;
                            im.setName(newImport);
                            changed[0] = true;
                        }
                    });

                    if (changed[0]) {
                        Files.writeString(file, LexicalPreservingPrinter.print(cu));
                    }

                } catch (Exception e) {
                    System.out.println("Skipping (pass2): " + file);
                }
            }

            // Write CSV — append if exists
            writeUsageCSV(projectPath, usageRows,"class");

            Path newPath = classFile.resolveSibling(newSimple + ".java");

            try {
                //  HARD CHECK: Do NOT overwrite existing file
                if (Files.exists(newPath)) {
                    throw new RuntimeException(
                        "Rename failed: Target file already exists → " + newPath.getFileName()
                    );
                }

                //  VALIDATION: Ensure source file still exists
                if (!Files.exists(classFile)) {
                    throw new RuntimeException(
                        "Rename failed: Source file missing → " + classFile.getFileName()
                    );
                }

                // ✅ ATOMIC MOVE (safer)
                Files.move(
                    classFile,
                    newPath,
                    StandardCopyOption.ATOMIC_MOVE
                );

                System.out.println("✅ File renamed safely: "
                        + classFile.getFileName() + " → "
                        + newPath.getFileName());

            } catch (Exception e) {
                throw new RuntimeException("❌ File rename failed safely (no overwrite done)", e);
            }
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Rename failed", e);
        }

    }

    // ================= SHARED CSV WRITER =================
    // Appends data rows to class-usage.csv if it exists, writes fresh if not
    private void writeUsageCSV(String projectPath, Set<String> usageRows, String type) {
        try {
        	Path usagePath = Paths.get(projectPath, type + "-usage.csv");
            if (usagePath.getParent() != null) {
                Files.createDirectories(usagePath.getParent());
            }

            if (Files.exists(usagePath)) {
                // Append only data rows (skip header)
                List<String> dataRows = usageRows.stream()
                        .skip(1)
                        .collect(Collectors.toList());
                Files.write(usagePath, dataRows,
                        java.nio.file.StandardOpenOption.APPEND,
                        java.nio.file.StandardOpenOption.CREATE);
            } else {
                // First write — include header
                Files.write(usagePath, usageRows);
            }
        } catch (IOException e) {
            System.err.println("⚠️ Failed to write usage CSV: " + e.getMessage());
        }
    }

    // ================= HELPERS =================

    private String extractSimpleName(String name) {
        return name.contains(".")
                ? name.substring(name.lastIndexOf('.') + 1)
                : name;
    }

    private boolean isRenameSafe(Path root, String oldName, String newName) {
        if (oldName.equals(newName)) return false;
        try {
            return Files.walk(root)
                    .noneMatch(p -> p.getFileName().toString().equals(newName + ".java"));
        } catch (Exception e) {
            return false;
        }
    }

    public void generateClassListCSV(String projectPath, String csvPath) {
        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {
            StringBuilder sb = new StringBuilder();
            sb.append("OldClass,NewClass\n");

            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(path -> {
                     try {
                         CompilationUnit cu = StaticJavaParser.parse(path);
                         cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cd -> {
                             String className = cd.getNameAsString();
                             sb.append(className).append(",").append(className).append("\n");
                         });
                     } catch (Exception e) {
                         System.err.println("Error reading: " + path);
                     }
                 });

            Path path = Paths.get(csvPath);
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.writeString(path, sb.toString());

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV", e);
        }
    }

    public void updateCSV(String csvPath, String oldName, String newName) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(csvPath));
            StringBuilder updated = new StringBuilder();
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length >= 2 && parts[0].trim().equals(oldName)) {
                    updated.append(oldName).append(",").append(newName).append("\n");
                } else {
                    updated.append(line).append("\n");
                }
            }
            Files.writeString(Paths.get(csvPath), updated.toString());
        } catch (IOException e) {
            throw new RuntimeException("CSV update failed", e);
        }
    }

    public List<String[]> readCSV(String csvPath) {
        try {
            return Files.readAllLines(Paths.get(csvPath))
                    .stream()
                    .skip(1)
                    .map(line -> line.split(","))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("CSV read failed", e);
        }
    }

    public void generateFinalReportCSV(String mappingCsv, String usageCsv, String outputCsv) {
        try {
            // mapping CSV is optional for variable/method renames — use identity map if missing
            Map<String, String> renameMap = new HashMap<>();
            if (Files.exists(Paths.get(mappingCsv))) {
                List<String> mappingLines = Files.readAllLines(Paths.get(mappingCsv));
                mappingLines.stream().skip(1).forEach(line -> {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) renameMap.put(parts[0].trim(), parts[1].trim());
                });
            }

            List<String> usageLines = Files.readAllLines(Paths.get(usageCsv));
            StringBuilder finalCsv = new StringBuilder();
            finalCsv.append("OldName,NewName,UsedInFile,LineNumber,EntityType,UsageType");
            Set<String> uniqueRows = new HashSet<>();

            for (String line : usageLines.stream().skip(1).toList()) {
                String[] parts = line.split(",");
                if (parts.length < 5) continue;
                String oldName = parts[0].trim();
                // Use mapping if available, otherwise use what's already in the CSV
                String mappedNew = renameMap.getOrDefault(oldName, parts[1].trim());
                String mergedRow = oldName + "," + mappedNew + "," + parts[2] + "," + parts[3] + "," + parts[4];
                if (uniqueRows.add(mergedRow)) {
                    finalCsv.append(mergedRow).append("\n");
                }
            }

            Path out = Paths.get(outputCsv);
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.writeString(out, finalCsv.toString());

        } catch (IOException e) {
            throw new RuntimeException("Final CSV generation failed", e);
        }
    }
    
    public void generateFinalReportCSVFromMultiple(List<String> usageFiles, String outputCsv) {

        try {
            StringBuilder finalCsv = new StringBuilder();
            finalCsv.append("OldName,NewName,UsedInFile,LineNumber,EntityType,UsageType");

            Set<String> unique = new HashSet<>();

            for (String file : usageFiles) {

                if (!Files.exists(Paths.get(file))) continue;

                List<String> lines = Files.readAllLines(Paths.get(file));

                for (String line : lines.stream().skip(1).toList()) {

                    if (unique.add(line)) {
                        finalCsv.append(line).append("\n");
                    }
                }
            }

            Path out = Paths.get(outputCsv);
            Files.createDirectories(out.getParent());
            Files.writeString(out, finalCsv.toString());

        } catch (Exception e) {
            throw new RuntimeException("Final merge failed", e);
        }
    }
}