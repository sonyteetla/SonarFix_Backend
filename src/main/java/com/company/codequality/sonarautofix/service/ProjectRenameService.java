package com.company.codequality.sonarautofix.service;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
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
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
@Service
public class ProjectRenameService {

	public void renameVariableInProject(String projectPath, String oldName, String newName) {

	    if (oldName.equals(newName)) return;

	    Set<String> usageRows = new LinkedHashSet<>();
	    usageRows.add("OldName,NewName,UsedInFile,LineNumber,EntityType,UsageType");

	    try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {

	        paths.filter(Files::isRegularFile)
	             .filter(p -> p.toString().endsWith(".java"))
	             .filter(p -> !shouldSkipFile(p))
	             .forEach(path -> {

	                 boolean[] changed = {false};

	                 try {
	                     CompilationUnit cu = StaticJavaParser.parse(path);
	                     LexicalPreservingPrinter.setup(cu);

	                     // Skip DTO/Repository packages
	                     if (cu.getPackageDeclaration().isPresent()) {
	                         String pkg = cu.getPackageDeclaration().get()
	                                 .getNameAsString().toLowerCase();
	                         if (pkg.contains("repository") ||
	                        		    pkg.contains("dto")        ||
	                        		    pkg.contains("model")      ||
	                        		    pkg.contains("entity")     ||   // ← NEW
	                        		    pkg.contains("entities"))  return;
	                     }

	                     // ─────────────────────────────────────────────
	                     // STEP 1 — FIELD DECLARATIONS
	                     // Catches: private final TransactionService transService;
	                     // VariableDeclarator inside FieldDeclaration
	                     // ─────────────────────────────────────────────
	                     cu.findAll(FieldDeclaration.class).forEach(field -> {
	                         field.getVariables().forEach(var -> {
	                             if (var.getNameAsString().equals(oldName)) {
	                                 int line = var.getBegin().map(p -> p.line).orElse(-1);
	                                 usageRows.add(oldName + "," + newName + ","
	                                         + path.getFileName() + "," + line
	                                         + ",VARIABLE,FIELD_DECLARATION");
	                                 var.setName(newName);
	                                 changed[0] = true;
	                             }
	                         });
	                     });

	                     // ─────────────────────────────────────────────
	                     // STEP 2 — LOCAL VARIABLE DECLARATIONS
	                     // Catches: Map<String, Long> stat = new HashMap<>();
	                     // ─────────────────────────────────────────────
	                     cu.findAll(VariableDeclarator.class).forEach(var -> {

	                         // skip if already handled as field above
	                         if (var.getParentNode()
	                                 .map(p -> p instanceof FieldDeclaration)
	                                 .orElse(false)) return;

	                         if (var.getNameAsString().equals(oldName)) {
	                             int line = var.getBegin().map(p -> p.line).orElse(-1);
	                             usageRows.add(oldName + "," + newName + ","
	                                     + path.getFileName() + "," + line
	                                     + ",VARIABLE,LOCAL_DECLARATION");
	                             var.setName(newName);
	                             changed[0] = true;
	                         }
	                     });

	                     // ─────────────────────────────────────────────
	                     // STEP 3 — ALL NameExpr USAGES
	                     // FIX: Do NOT rely on resolved.isVariable() —
	                     // symbol resolution fails often for local vars.
	                     // Instead: rename every NameExpr whose text matches
	                     // oldName, provided it is NOT a method name
	                     // (MethodCallExpr scope) or a type reference.
	                     // ─────────────────────────────────────────────
	                     cu.findAll(NameExpr.class).forEach(ne -> {

	                         if (!ne.getNameAsString().equals(oldName)) return;

	                         // Guard: skip if this NameExpr is the callee of a
	                         // MethodCallExpr (e.g., someMethod()), not a variable
	                         boolean isMethodCall = ne.getParentNode()
	                                 .map(p -> p instanceof MethodCallExpr
	                                         && ((MethodCallExpr) p)
	                                                 .getNameAsString().equals(oldName))
	                                 .orElse(false);

	                         if (isMethodCall) return;

	                         // Try symbol resolution first (strict path)
	                         boolean renamed = false;
	                         try {
	                             ResolvedValueDeclaration resolved = ne.resolve();
	                             // isVariable() = local var, isField() = class field
	                             if (resolved.isVariable() || resolved.isField()) {
	                                 ne.setName(newName);
	                                 int line = ne.getBegin().map(p -> p.line).orElse(-1);
	                                 usageRows.add(oldName + "," + newName + ","
	                                         + path.getFileName() + "," + line
	                                         + ",VARIABLE,USAGE");
	                                 changed[0] = true;
	                                 renamed = true;
	                             }
	                         } catch (Exception ignored) {
	                             // symbol solver unavailable — fall through to
	                             // name-based fallback below
	                         }

	                         // ── FALLBACK (no symbol solver / unresolvable) ──
	                         // If we already renamed a declaration of oldName in
	                         // this file (changed[0] == true from steps 1/2),
	                         // it is safe to rename matching NameExprs too.
	                         if (!renamed && changed[0]) {
	                             ne.setName(newName);
	                             int line = ne.getBegin().map(p -> p.line).orElse(-1);
	                             usageRows.add(oldName + "," + newName + ","
	                                     + path.getFileName() + "," + line
	                                     + ",VARIABLE,USAGE_FALLBACK");
	                             // changed[0] already true
	                         }
	                     });

	                     // ─────────────────────────────────────────────
	                     // STEP 4 — GETTER / SETTER RENAME
	                     // e.g., getTransService() → getTransactionService()
	                     // ─────────────────────────────────────────────
	                     String oldCap = capitalize(oldName);
	                     String newCap = capitalize(newName);
	                     String oldGetter = "get" + oldCap;
	                     String newGetter = "get" + newCap;
	                     String oldSetter = "set" + oldCap;
	                     String newSetter = "set" + newCap;

	                     cu.findAll(MethodCallExpr.class).forEach(mc -> {

	                         if (isBuilderCall(mc)) return;

	                         String name = mc.getNameAsString();
	                         int line = mc.getBegin().map(p -> p.line).orElse(-1);

	                         if (name.equals(oldGetter)) {
	                             usageRows.add(oldName + "," + newName + ","
	                                     + path.getFileName() + "," + line
	                                     + ",VARIABLE,GETTER_CALL");
	                             mc.setName(newGetter);
	                             changed[0] = true;
	                         }

	                         if (name.equals(oldSetter)) {
	                             usageRows.add(oldName + "," + newName + ","
	                                     + path.getFileName() + "," + line
	                                     + ",VARIABLE,SETTER_CALL");
	                             mc.setName(newSetter);
	                             changed[0] = true;
	                         }
	                     });

	                     // ─────────────────────────────────────────────
	                     // STEP 5 — GETTER / SETTER METHOD DECLARATIONS
	                     // Renames the actual method body definitions too
	                     // ─────────────────────────────────────────────
	                     cu.findAll(MethodDeclaration.class).forEach(md -> {

	                         String name = md.getNameAsString();
	                         int line = md.getBegin().map(p -> p.line).orElse(-1);

	                         if (name.equals(oldGetter)) {
	                             usageRows.add(oldName + "," + newName + ","
	                                     + path.getFileName() + "," + line
	                                     + ",VARIABLE,GETTER_DECLARATION");
	                             md.setName(newGetter);
	                             changed[0] = true;
	                         }

	                         if (name.equals(oldSetter)) {
	                             usageRows.add(oldName + "," + newName + ","
	                                     + path.getFileName() + "," + line
	                                     + ",VARIABLE,SETTER_DECLARATION");
	                             md.setName(newSetter);
	                             changed[0] = true;
	                         }
	                     });

	                     if (changed[0]) {
	                         Files.writeString(path, LexicalPreservingPrinter.print(cu));
	                     }

	                 } catch (Exception e) {
	                     System.err.println("Failed: " + path + " → " + e.getMessage());
	                 }
	             });

	    } catch (Exception e) {
	        throw new RuntimeException(e);
	    }

	    writeUsageCSV(projectPath, usageRows, "variable");
	}
	
	public void renameMethodInProject(String projectPath, String oldName, String newName) {

	    if (isGetterOrSetter(oldName) || isRepositoryMethod(oldName)) {
	        System.out.println("⛔ Skipping unsafe method rename: " + oldName);
	        return;
	    }

	    Set<String> usageRows = new LinkedHashSet<>();
	    usageRows.add("OldName,NewName,UsedInFile,LineNumber,EntityType,UsageType");

	    // ── PASS 1: find and rename all DECLARATIONS first ──────────
	    // Track which files had their declaration renamed so call-site
	    // pass knows the rename is confirmed real.
	    Set<String> confirmedRename = new LinkedHashSet<>();

	    List<Path> allFiles;
	    try {
	        allFiles = Files.walk(Paths.get(projectPath))
	                .filter(Files::isRegularFile)
	                .filter(p -> p.toString().endsWith(".java"))
	                .filter(p -> !shouldSkipFile(p))
	                .collect(Collectors.toList());
	    } catch (IOException e) {
	        throw new RuntimeException(e);
	    }

	    for (Path path : allFiles) {
	        try {
	            CompilationUnit cu = StaticJavaParser.parse(path);
	            LexicalPreservingPrinter.setup(cu);

	            if (cu.getPackageDeclaration().isPresent()) {
	                String pkg = cu.getPackageDeclaration().get()
	                        .getNameAsString().toLowerCase();
	                if (pkg.contains("repository") ||
	                	    pkg.contains("dto")        ||
	                	    pkg.contains("model")      ||
	                	    pkg.contains("entity")     ||   // ← NEW
	                	    pkg.contains("entities"))   continue;
	            }

	            boolean[] changed = {false};

	            cu.findAll(MethodDeclaration.class).forEach(md -> {
	                if (!md.getNameAsString().equals(oldName)) return;
	                if (isUnsafeMethodRename(md)) return;
	                if (isGetterOrSetter(md.getNameAsString())) return;
	                if (isRepositoryMethod(md.getNameAsString())) return;

	                int line = md.getBegin().map(p -> p.line).orElse(-1);
	                usageRows.add(oldName + "," + newName + ","
	                        + path.getFileName() + "," + line
	                        + ",METHOD,DECLARATION");
	                md.setName(newName);
	                changed[0] = true;
	                confirmedRename.add(oldName); // ← mark this rename as confirmed
	            });

	            if (changed[0]) {
	                Files.writeString(path, LexicalPreservingPrinter.print(cu));
	            }

	        } catch (Exception e) {
	            System.err.println("Pass1 error: " + path + " → " + e.getMessage());
	        }
	    }

	    // If no declaration was found anywhere, abort — nothing to rename
	    if (!confirmedRename.contains(oldName)) {
	        System.err.println("⚠️  No declaration found for: " + oldName + " — aborting call-site rename.");
	        writeUsageCSV(projectPath, usageRows, "method");
	        return;
	    }

	    // ── PASS 2: rename all CALL SITES across all files ───────────
	    // Now safe to rename calls because declaration is confirmed renamed.
	    for (Path path : allFiles) {
	        try {
	            CompilationUnit cu = StaticJavaParser.parse(path);
	            LexicalPreservingPrinter.setup(cu);

	            if (cu.getPackageDeclaration().isPresent()) {
	                String pkg = cu.getPackageDeclaration().get()
	                        .getNameAsString().toLowerCase();
	                if (pkg.contains("repository") ||
	                	    pkg.contains("dto")        ||
	                	    pkg.contains("model")      ||
	                	    pkg.contains("entity")     ||   // ← NEW
	                	    pkg.contains("entities"))  continue;
	            }

	            boolean[] changed = {false};

	            // ── Method calls ──────────────────────────────────────
	            cu.findAll(MethodCallExpr.class).forEach(mc -> {
	                if (!mc.getNameAsString().equals(oldName)) return;
	                if (isBuilderCall(mc)) return;
	                if (isGetterOrSetter(mc.getNameAsString())) return;
	                if (isRepositoryMethod(mc.getNameAsString())) return;

	                // Try strict resolution first
	                boolean renamed = false;
	                try {
	                    ResolvedMethodDeclaration resolved = mc.resolve();
	                    if (isFrameworkMethod(resolved)) return;

	                    int line = mc.getBegin().map(p -> p.line).orElse(-1);
	                    usageRows.add(oldName + "," + newName + ","
	                            + path.getFileName() + "," + line
	                            + ",METHOD,CALL");
	                    mc.setName(newName);
	                    changed[0] = true;
	                    renamed = true;

	                } catch (Exception ignored) {}

	                // Fallback: declaration confirmed globally — safe to rename
	                if (!renamed) {
	                    int line = mc.getBegin().map(p -> p.line).orElse(-1);
	                    usageRows.add(oldName + "," + newName + ","
	                            + path.getFileName() + "," + line
	                            + ",METHOD,CALL_FALLBACK");
	                    mc.setName(newName);
	                    changed[0] = true;
	                }
	            });

	            // ── Method references ─────────────────────────────────
	            cu.findAll(MethodReferenceExpr.class).forEach(mr -> {
	                if (!mr.getIdentifier().equals(oldName)) return;
	                if (isGetterOrSetter(mr.getIdentifier())) return;
	                if (isRepositoryMethod(mr.getIdentifier())) return;

	                int line = mr.getBegin().map(p -> p.line).orElse(-1);
	                usageRows.add(oldName + "," + newName + ","
	                        + path.getFileName() + "," + line
	                        + ",METHOD,REFERENCE");
	                mr.setIdentifier(newName);
	                changed[0] = true;
	            });

	            if (changed[0]) {
	                Files.writeString(path, LexicalPreservingPrinter.print(cu));
	            }

	        } catch (Exception e) {
	            System.err.println("Pass2 error: " + path + " → " + e.getMessage());
	        }
	    }

	    writeUsageCSV(projectPath, usageRows, "method");
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

                    //  Extract simple name from type string before comparing.
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
                        // Use extractSimpleName() — same reason as above
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

                //  ATOMIC MOVE (safer)
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
            sb.append("OldClassName,NewClassName\n");

            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(path -> {
                     try {
                         CompilationUnit cu = StaticJavaParser.parse(path);
                         cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cd -> {
                             String className = cd.getNameAsString();
                             sb.append(className).append(",").append("\n");
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
                String[] parts = line.split("\\s*,\\s*");
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
                    .map(line -> line.split("\\s*,\\s*"))
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
                    String[] parts = line.split("\\s*,\\s*");
                    if (parts.length >= 2) renameMap.put(parts[0].trim(), parts[1].trim());
                });
            }

            List<String> usageLines = Files.readAllLines(Paths.get(usageCsv));
            StringBuilder finalCsv = new StringBuilder();
            finalCsv.append("OldName,NewName,UsedInFile,LineNumber,EntityType,UsageType");
            Set<String> uniqueRows = new HashSet<>();

            for (String line : usageLines.stream().skip(1).toList()) {
                String[] parts = line.split("\\s*,\\s*");
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
    
    public void renameFromCSV(String projectPath, String csvPath) {

        List<String[]> rows = readCSV(csvPath);

        Set<String> seen = new HashSet<>();

        String mappingPath = projectPath + "/class-mapping.csv";

        if (!Files.exists(Paths.get(mappingPath))) {
            generateClassListCSV(projectPath, mappingPath);
        }

        // VALIDATION
        for (String[] row : rows) {

            if (row.length < 3) continue;

            String type = row[0].trim().toLowerCase();
            String oldName = row[1].trim();

            String key = type + ":" + oldName;

            if (!seen.add(key)) {
                throw new RuntimeException("Duplicate rename: " + key);
            }
        }

        // EXECUTION
        for (String[] row : rows) {

            if (row.length < 3) continue;

            String type = row[0].trim().toLowerCase();
            String oldName = row[1].trim();
            String newName = row[2].trim();

            switch (type) {

                case "class":

                    boolean success =
                        renameClassInProject(projectPath, oldName, newName);

                    if (success) {
                        updateCSV(mappingPath, oldName, newName);
                        System.out.println("Mapping updated: " + oldName + " -> " + newName);
                    } else {
                        System.out.println("Rename failed: " + oldName);
                    }

                    break;

                case "method":
                    renameMethodInProject(projectPath, oldName, newName);
                    break;

                case "variable":
                    renameVariableInProject(projectPath, oldName, newName);
                    break;

                default:
                    System.out.println("Invalid type: " + type);
            }
        }
    }
    
    public void renameFromCSVByType(String projectPath, String csvPath, String type) throws IOException {

        List<String[]> rows = readCSV(csvPath);

        Set<String> seen = new HashSet<>();

        String mappingPath = projectPath + "/class-mapping.csv";

        //  ALWAYS ensure mapping exists (not conditional logic later)
        if (!Files.exists(Paths.get(mappingPath))) {
            generateClassListCSV(projectPath, mappingPath);
        }

        //  HEADER VALIDATION
        List<String> lines = Files.readAllLines(Paths.get(csvPath));
        if (lines.isEmpty()) {
            throw new RuntimeException("Empty CSV file");
        }

        String header = lines.get(0).toLowerCase();

        switch (type) {
            case "class":
                if (!header.contains("oldclass")) {
                    throw new RuntimeException("Invalid Class CSV format");
                }
                break;
            case "method":
                if (!header.contains("oldmethod")) {
                    throw new RuntimeException("Invalid Method CSV format");
                }
                break;
            case "variable":
                if (!header.contains("oldvariable")) {
                    throw new RuntimeException("Invalid Variable CSV format");
                }
                break;
        }

        //  DUPLICATE CHECK
        for (String[] row : rows) {
            if (row.length < 2) continue;

            String oldName = row[0].trim();

            if (!seen.add(oldName)) {
                throw new RuntimeException("Duplicate rename: " + oldName);
            }
        }

        //  EXECUTION
        for (String[] row : rows) {

            if (row.length < 2) continue;

            String oldName = row[0].trim();
            String newName = row[1].trim();

            if (newName.isEmpty()) continue;

            switch (type) {

                case "class":
                    boolean success = renameClassInProject(projectPath, oldName, newName);

                    if (success) {
                        
                        // ALWAYS update mapping (overwrite or append)
                        updateOrInsertMapping(mappingPath, oldName, newName);
                    }
                    break;

                case "method":
                    renameMethodInProject(projectPath, oldName, newName);
                    break;

                case "variable":
                    renameVariableInProject(projectPath, oldName, newName);
                    break;
            }
        }
    }
    
    private boolean isUnsafeVariable(VariableDeclarator v) {

        // ── Check if inside an @Entity or @Table class ───────────────
        // Even if entity folder name is non-standard, annotation catches it
        boolean isInsideEntity = v.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(cls -> cls.getAnnotations().stream()
                        .anyMatch(a -> {
                            String n = a.getNameAsString();
                            return n.equals("Entity")      ||
                                   n.equals("Table")       ||
                                   n.equals("MappedSuperclass") ||
                                   n.equals("Embeddable");
                        }))
                .orElse(false);

        if (isInsideEntity) return true;  

        Optional<FieldDeclaration> field = v.findAncestor(FieldDeclaration.class);

        if (field.isPresent()) {

            FieldDeclaration fd = field.get();

            // Skip injected fields (@Autowired, @Inject, @Value)
            boolean isInjected = fd.getAnnotations().stream()
                    .anyMatch(a -> {
                        String n = a.getNameAsString();
                        return n.equals("Autowired") ||
                               n.equals("Inject")    ||
                               n.equals("Value");      
                    });
            if (isInjected) return true;

            // Skip JPA column/relation fields by annotation
            boolean isJpaField = fd.getAnnotations().stream()
                    .anyMatch(a -> {
                        String n = a.getNameAsString();
                        return n.equals("Column")       ||
                               n.equals("Id")           ||
                               n.equals("GeneratedValue")||
                               n.equals("OneToMany")    ||
                               n.equals("ManyToOne")    ||
                               n.equals("OneToOne")     ||
                               n.equals("ManyToMany")   ||
                               n.equals("JoinColumn")   ||
                               n.equals("Enumerated")   ||
                               n.equals("Transient");
                    });
            if (isJpaField) return true;

            // Skip constants (static final)
            if (fd.isStatic() && fd.isFinal()) return true;

            // Skip repository / dto / model / entity typed fields
            String type = fd.getElementType().asString().toLowerCase();
            if (type.contains("repository") ||
                type.contains("dto")        ||
                type.contains("model")      ||
                type.contains("entity")) return true;
        }

        // Skip trivial names (single char or 2-char like id, pk)
        if (v.getNameAsString().length() <= 2) return true;

        return false;
    }
    
    public void generateMethodListCSV(String projectPath, String csvPath) {

        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {

            StringBuilder sb = new StringBuilder();
            sb.append("OldMethodName,NewMethodName,Locations\n");

            Map<String, List<String>> methodMap = new HashMap<>();

            paths.filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> !shouldSkipFile(p))          // path-level: dto/model/repository
                 .forEach(path -> {

                     try {
                         CompilationUnit cu = StaticJavaParser.parse(path);

                         // Package-level filter
                         if (cu.getPackageDeclaration().isPresent()) {
                             String pkg = cu.getPackageDeclaration().get()
                                     .getNameAsString().toLowerCase();
                             if (pkg.contains("repository") ||
                            		    pkg.contains("dto")        ||
                            		    pkg.contains("model")      ||
                            		    pkg.contains("entity")     ||   
                            		    pkg.contains("entities"))  return;
                         }

                         cu.findAll(MethodDeclaration.class).forEach(md -> {

                             // Skip @Override / interface / framework-blocked methods
                             if (isUnsafeMethodRename(md)) return;

                             String name = md.getNameAsString();

                             // Skip getters and setters
                             if (isGetterOrSetter(name)) return;

                             // Skip repository-style finder methods
                             if (isRepositoryMethod(name)) return;

                             int line = md.getBegin().map(p -> p.line).orElse(-1);
                             String location = path.getFileName() + ":" + line;

                             methodMap.computeIfAbsent(name, k -> new ArrayList<>())
                                      .add(location);
                         });

                     } catch (Exception ignored) {}
                 });

            for (Map.Entry<String, List<String>> entry : methodMap.entrySet()) {
                String name = entry.getKey();
                String locations = String.join(";", entry.getValue());
                sb.append(name).append(",").append(",").append(locations).append("\n");
            }

            Files.writeString(Paths.get(csvPath), sb.toString());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private void updateOrInsertMapping(String csvPath, String oldName, String newName) {

        try {
            Path path = Paths.get(csvPath);

            List<String> lines = Files.readAllLines(path);
            StringBuilder updated = new StringBuilder();

            boolean found = false;

            for (String line : lines) {
                String[] parts = line.split("\\s*,\\s*");

                if (parts.length >= 2 && parts[0].trim().equals(oldName)) {
                    updated.append(oldName).append(",").append(newName).append("\n");
                    found = true;
                } else {
                    updated.append(line).append("\n");
                }
            }

            //  if mapping not found → ADD it
            if (!found) {
                updated.append(oldName).append(",").append(newName).append("\n");
            }

            Files.writeString(path, updated.toString());

        } catch (IOException e) {
            throw new RuntimeException("Mapping update failed", e);
        }
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
 // ================= SAFETY HELPERS =================
    private boolean shouldSkipFile(Path path) {
        String filePath = path.toString().toLowerCase();

        return filePath.contains("/dto/")         ||
               filePath.contains("\\dto\\")       ||
               filePath.contains("/model/")       ||
               filePath.contains("\\model\\")     ||
               filePath.contains("/entity/")      || // ← NEW
               filePath.contains("\\entity\\")    || // ← NEW
               filePath.contains("/entities/")    || // ← NEW
               filePath.contains("\\entities\\")  || // ← NEW
               filePath.contains("/repository/")  ||
               filePath.contains("\\repository\\") ||
               filePath.endsWith("dto.java")      ||
               filePath.endsWith("model.java")    ||
               filePath.endsWith("repository.java");
    }

    private boolean isUnsafeMethodRename(MethodDeclaration md) {

        // Block @Override methods
        if (md.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals("Override"))) {
            return true;
        }

        // Block JPA lifecycle annotations
        Set<String> lifecycleAnnotations = Set.of(
            "PrePersist", "PreUpdate", "PreRemove",
            "PostPersist", "PostUpdate", "PostRemove", "PostLoad"
        );
        if (md.getAnnotations().stream()
                .anyMatch(a -> lifecycleAnnotations.contains(a.getNameAsString()))) {
            return true;
        }

        // Block interface methods
        if (md.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(ClassOrInterfaceDeclaration::isInterface)
                .orElse(false)) {
            return true;
        }

        // Block by method name (framework + lifecycle + entry points)
        Set<String> blocked = Set.of(
            "addCorsMappings", "configure", "filterChain",
            "equals", "hashCode", "toString",
            "main", "run", "init", "destroy", "close",
            "save", "saveAll", "saveAndFlush",
            "update", "updateAll",
            "delete", "deleteAll", "deleteById",
            "flush", "persist", "merge",
            "prePersist", "preUpdate", "preRemove",
            "postPersist", "postUpdate", "postRemove", "postLoad",
            "passwordEncoder", "corsConfigurer", "corsFilter",
            "authenticationManager", "afterPropertiesSet",
            "onApplicationEvent","generateAccountNumber"
        );

        return blocked.contains(md.getNameAsString());
    }

    private boolean isFrameworkMethod(ResolvedMethodDeclaration resolved) {
        String cls = resolved.declaringType().getQualifiedName();

        return cls.startsWith("org.springframework") ||
               cls.startsWith("java.") ||
               cls.startsWith("jakarta.");
    }

    private boolean isBuilderCall(MethodCallExpr mc) {
        return mc.getScope()
                .map(s -> s.toString().toLowerCase().contains("builder"))
                .orElse(false);
    }

   
    public void generateVariableListCSV(String projectPath, String csvPath) {

        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {

            StringBuilder sb = new StringBuilder();
            sb.append("OldVariableName,NewVariableName,Locations\n");

            Map<String, List<String>> varMap = new HashMap<>();

            paths.filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> !shouldSkipFile(p))          // path-level: dto/model/repository
                 .forEach(path -> {

                     try {
                         CompilationUnit cu = StaticJavaParser.parse(path);

                         // Package-level filter (stronger — catches non-standard folder names)
                         if (cu.getPackageDeclaration().isPresent()) {
                             String pkg = cu.getPackageDeclaration().get()
                                     .getNameAsString().toLowerCase();
                             if (pkg.contains("repository") ||
                            		    pkg.contains("dto")        ||
                            		    pkg.contains("model")      ||
                            		    pkg.contains("entity")     ||   // ← NEW
                            		    pkg.contains("entities"))  return; // ← NEW
                         }

                         cu.findAll(VariableDeclarator.class).forEach(v -> {

                             String name = v.getNameAsString();

                             // Skip unsafe / injected / constant / dto / model vars
                             if (isUnsafeVariable(v)) return;

                             // Skip getter/setter-style names (avoids collision with method rename)
                             if (isGetterOrSetter(name)) return;

                             int line = v.getBegin().map(p -> p.line).orElse(-1);
                             String location = path.getFileName() + ":" + line;

                             varMap.computeIfAbsent(name, k -> new ArrayList<>())
                                   .add(location);
                         });

                     } catch (Exception ignored) {}
                 });

            for (Map.Entry<String, List<String>> entry : varMap.entrySet()) {
                String name = entry.getKey();
                String locations = String.join(";", entry.getValue());
                sb.append(name).append(",").append(",").append(locations).append("\n");
            }

            Files.writeString(Paths.get(csvPath), sb.toString());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
 // ── NEW HELPER: detects getters and setters by name pattern ──
    private boolean isGetterOrSetter(String methodName) {
        if (methodName == null) return false;

        // Standard JavaBean getter/setter: getFoo(), setFoo(), isFoo()
        if ((methodName.startsWith("get") || methodName.startsWith("set"))
                && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) {
            return true;
        }

        // Boolean getter: isFoo()
        if (methodName.startsWith("is")
                && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))) {
            return true;
        }

        return false;
    }

    // ── NEW HELPER: detects repository-style query methods ──────
    private boolean isRepositoryMethod(String methodName) {
        if (methodName == null) return false;

        // ── EXACT BLOCK LIST ────────────────────────────────────────
        Set<String> exactBlocked = Set.of(
            "save", "saveAll", "saveAndFlush",
            "update", "updateAll",
            "delete", "deleteAll", "deleteById",
            "flush", "persist", "merge", "refresh",
            "prePersist", "preUpdate", "preRemove",
            "postPersist", "postUpdate", "postRemove", "postLoad",
            "main", "run", "init", "destroy", "close",
            "passwordEncoder", "corsConfigurer", "corsFilter",
            "filterChain", "authenticationManager",
            "onApplicationEvent", "afterPropertiesSet"
        );
        if (exactBlocked.contains(methodName)) return true;

        // ── PREFIX + UPPERCASE SUFFIX PATTERNS ──────────────────────
        String[] repoPrefixes = {
            "findBy", "findAllBy", "findFirst", "findTop",
            "countBy", "existsBy",
            "deleteBy", "removeBy",        
            "deleteAllBy",                 
            "deleteAccountsBy",            
            "searchBy", "readBy",
            "queryBy", "streamBy",
            "getAll", "fetchAll","countStatusBy","countLoansBy", "deleteAccountBy"
        };

        for (String prefix : repoPrefixes) {
            if (methodName.startsWith(prefix)
                    && methodName.length() > prefix.length()
                    && Character.isUpperCase(methodName.charAt(prefix.length()))) {
                return true;
            }
        }

        return false;
    }
}