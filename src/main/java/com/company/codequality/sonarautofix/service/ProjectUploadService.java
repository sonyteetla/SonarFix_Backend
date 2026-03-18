package com.company.codequality.sonarautofix.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProjectUploadService {

    private static final String WORKSPACE = "C:/sonar-workspace/";

    private void ensureWorkspace() throws IOException {
        Files.createDirectories(Paths.get(WORKSPACE));
    }

    // ================= ZIP Upload =================
    public String handleZipUpload(MultipartFile file) {

        try {

            ensureWorkspace();

            String projectDir = WORKSPACE + System.currentTimeMillis();
            Path projectPath = Paths.get(projectDir);

            Files.createDirectories(projectPath);

            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {

                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {

                    Path newFile = projectPath.resolve(entry.getName()).normalize();

                    if (!newFile.startsWith(projectPath)) {
                        throw new IOException("Invalid ZIP entry: " + entry.getName());
                    }

                    if (entry.isDirectory()) {

                        Files.createDirectories(newFile);

                    } else {

                        Files.createDirectories(newFile.getParent());

                        try (OutputStream fos = Files.newOutputStream(newFile)) {

                            byte[] buffer = new byte[8192];
                            int len;

                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                }
            }

            // ===== Detect if ZIP has a single root folder =====

            Path projectRoot = projectPath;

            try (var stream = Files.list(projectPath)) {

                List<Path> children =
                        stream.collect(Collectors.toList());

                if (children.size() == 1 && Files.isDirectory(children.get(0))) {

                    projectRoot = children.get(0);

                    System.out.println("Detected root folder inside ZIP: " + projectRoot);
                }
            }

            return projectRoot.toString();

        } catch (Exception e) {

            throw new RuntimeException("ZIP Upload failed: " + e.getMessage(), e);
        }
    }

    // ================= GitHub Clone =================
    public String cloneGithub(String repoUrl) {

        try {

            ensureWorkspace();

            String projectDir = WORKSPACE + System.currentTimeMillis();

            ProcessBuilder builder =
                    new ProcessBuilder(
                            "C:\\Program Files\\Git\\bin\\git.exe",
                            "clone",
                            repoUrl,
                            projectDir
                    );

            builder.redirectErrorStream(true);

            Process process = builder.start();

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(process.getInputStream()))) {

                while (reader.readLine() != null) {}
            }

            int exit = process.waitFor();

            if (exit != 0) {
                throw new RuntimeException("Git clone failed. Exit code: " + exit);
            }

            return projectDir;

        } catch (Exception e) {

            throw new RuntimeException("Git clone failed: " + e.getMessage(), e);
        }
    }

    // ================= Local Directory Copy =================
    public String useLocalDirectory(String localPath) {

        Path src = Paths.get(localPath);

        if (!Files.exists(src) || !Files.isDirectory(src)) {
            throw new RuntimeException("Invalid local directory");
        }

        try {

            ensureWorkspace();

            String projectDir = WORKSPACE + System.currentTimeMillis();
            Path dest = Paths.get(projectDir);

            Files.walk(src).forEach(source -> {

                try {

                    Path target = dest.resolve(src.relativize(source));

                    if (Files.isDirectory(source)) {

                        Files.createDirectories(target);

                    } else {

                        Files.createDirectories(target.getParent());

                        Files.copy(
                                source,
                                target,
                                StandardCopyOption.REPLACE_EXISTING
                        );
                    }

                } catch (IOException e) {

                    throw new RuntimeException(e);
                }
            });

            return projectDir;

        } catch (IOException e) {

            throw new RuntimeException("Local directory copy failed: " + e.getMessage(), e);
        }
    }

    // ================= Copy Project =================
    public String copyProject(String originalPath) throws IOException {

        Path sourcePath = Paths.get(originalPath);

        String fixedPathStr =
                originalPath.endsWith("_fixed")
                        ? originalPath
                        : originalPath + "_fixed";

        Path fixedPath = Paths.get(fixedPathStr);

        if (Files.exists(fixedPath)) {
            deleteDirectory(fixedPath);
        }

        Files.walk(sourcePath)
                .filter(p -> {

                    String path = p.toString().toLowerCase();

                    return !path.contains(".git")
                            && !path.contains("target")
                            && !path.contains(".idea")
                            && !path.contains(".metadata")     // 🔥 FIX
                            && !path.contains("node_modules")
                            && !path.contains("gpucache")      // 🔥 FIX
                            && !path.contains("plugins")       // 🔥 FIX
                            && !path.endsWith(".class")
                            && !path.endsWith(".jar")
                            && !path.endsWith(".cbl");
                })
                .forEach(source -> {

                    try {

                        Path target =
                                fixedPath.resolve(sourcePath.relativize(source));

                        if (Files.isDirectory(source)) {

                            Files.createDirectories(target);

                        } else {

                            Files.createDirectories(target.getParent());

                            try {
                                Files.copy(
                                        source,
                                        target,
                                        StandardCopyOption.REPLACE_EXISTING
                                );
                            } catch (Exception e) {
                                System.out.println("⚠️ Skipping locked file: " + source);
                            }
                        }

                    } catch (Exception e) {
                        System.out.println("❌ Failed copying: " + source);
                    }
                });

        return fixedPath.toString();
    }

    // ================= Delete Directory =================
    private void deleteDirectory(Path path) throws IOException {

        if (!Files.exists(path)) return;

        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {

                    try {

                        if (p.toString().contains(".git")) return;

                        Files.deleteIfExists(p);

                    } catch (IOException ignored) {}
                });
    }
    // ================= Store Project Key =================
    public void registerProjectKey(String projectDir, String projectKey) {

        try {

            Path metadata =
                    Paths.get(projectDir, ".project-key");

            Files.writeString(metadata, projectKey);

        } catch (IOException e) {

            throw new RuntimeException("Failed to store project key", e);
        }
    }
}