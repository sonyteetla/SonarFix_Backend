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

    // ================= IGNORE FILTER =================
    private boolean shouldIgnore(Path path) {
        String p = path.toString().replace("\\", "/");

        return p.contains("/.git/")
                || p.contains("/node_modules/")
                || p.contains("/target/")
                || p.contains("/build/");
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

                    if (shouldIgnore(newFile)) continue;

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

            Path projectRoot = projectPath;

            try (var stream = Files.list(projectPath)) {

                List<Path> children = stream.collect(Collectors.toList());

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
                    new ProcessBuilder("git", "clone", "--depth", "1", repoUrl, projectDir);

            builder.redirectErrorStream(true);

            Process process = builder.start();

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
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

            Files.walk(src)
                    .filter(p -> !shouldIgnore(p))
                    .forEach(source -> {

                        try {

                            Path target = dest.resolve(src.relativize(source));

                            if (Files.isDirectory(source)) {

                                Files.createDirectories(target);

                            } else {

                                Files.createDirectories(target.getParent());

                                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
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

    public String copyProject(String originalPath) throws IOException {
        Path sourcePath = Paths.get(originalPath).normalize();

        if (originalPath.endsWith("_fixed") || originalPath.endsWith("_fixed/")) {
             if (Files.exists(sourcePath)) {
                 System.out.println("Path is already fixed version: " + originalPath);
                 return originalPath;
             }
        }

        String fixedPathStr = originalPath.endsWith("/") || originalPath.endsWith("\\") 
                ? originalPath.substring(0, originalPath.length()-1) + "_fixed"
                : originalPath + "_fixed";
        
        Path fixedPath = Paths.get(fixedPathStr).normalize();
        
        System.out.println("Copying project from [" + sourcePath + "] to [" + fixedPath + "]");

        if (Files.exists(fixedPath)) {
            System.out.println("Target already exists, deleting first: " + fixedPath);
            deleteDirectory(fixedPath);
        }

        if (!Files.exists(sourcePath)) {
            throw new IOException("Source path does not exist: " + sourcePath);
        }

        Files.walk(sourcePath)
                .filter(p -> !shouldIgnore(p))
                .forEach(source -> {
                    try {
                        Path target = fixedPath.resolve(sourcePath.relativize(source));
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target);
                        } else {
                            if (target.getParent() != null) {
                                Files.createDirectories(target.getParent());
                            }
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Error copying " + source + " -> " + fixedPath, e);
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
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed deleting: " + p, e);
                    }
                });
    }

    // ================= METADATA =================
    public void registerProjectKey(String projectDir, String projectKey) {

        try {
            Path metadata = Paths.get(projectDir, ".project-key");
            Files.writeString(metadata, projectKey);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store project key", e);
        }
    }
}