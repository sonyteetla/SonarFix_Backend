package com.company.codequality.sonarautofix.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.UUID;

@Service
public class ProjectUploadService {

    @Value("${workspace.path}")
    private String workspace;

    @Value("${git.path}")
    private String gitPath;

    // ------------------ ZIP Upload ------------------

    // ZIP Upload

    public String handleZipUpload(MultipartFile file) {
        String projectDir = "";
        try {
            // Use UUID for unique project directory
            Path projectPath = Paths.get(workspace, UUID.randomUUID().toString());
            Files.createDirectories(projectPath);
            projectDir = projectPath.toAbsolutePath().toString();
            System.out.println("Extracting ZIP to: " + projectDir);

            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name == null || name.trim().isEmpty())
                        continue;

                    Path newFile = projectPath.resolve(name).normalize();

                    // Security check: ensure entry is within project directory
                    if (!newFile.startsWith(projectPath)) {
                        throw new IOException("Entry is outside of the target directory: " + name);
                    }

                    // On Windows, some ZIP tools don't set isDirectory but end with /
                    boolean representsDirectory = entry.isDirectory() || name.endsWith("/") || name.endsWith("\\");

                    if (representsDirectory) {
                        Files.createDirectories(newFile);
                    } else {
                        // It's a file. Ensure its parent directory exists.
                        Path parent = newFile.getParent();
                        if (parent != null && !Files.exists(parent)) {
                            Files.createDirectories(parent);
                        }

                        // Extract file content safely
                        System.out.println("Copying file entry: " + name);
                        Files.copy(zis, newFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            return projectDir;

        } catch (Exception e) {
            System.err.println("Upload failed at project directory: " + projectDir);
            e.printStackTrace();
            throw new RuntimeException("ZIP Upload failed: " + e.getMessage(), e);
        }
    }

    // ------------------ GitHub Clone ------------------

    // GitHub Clone

    public String cloneGithub(String repoUrl) {
        try {
            String projectDir = workspace + System.currentTimeMillis();

            ProcessBuilder builder = new ProcessBuilder(gitPath, "clone", repoUrl, projectDir);
            Process process = builder.start();
            process.waitFor();

            if (process.exitValue() != 0) {
                throw new RuntimeException("Git clone failed, exit code: " + process.exitValue());
            }

            return projectDir;

        } catch (Exception e) {
            throw new RuntimeException("Git clone failed: " + e.getMessage(), e);
        }
    }

    // ------------------ Local Directory ------------------

    // Local Directory

    public String useLocalDirectory(String localPath) {
        Path src = Paths.get(localPath);

        if (!Files.exists(src) || !Files.isDirectory(src)) {
            throw new RuntimeException("Invalid local directory");
        }

        Path projectPath = Paths.get(workspace, UUID.randomUUID().toString());
        String projectDir = projectPath.toAbsolutePath().toString();
        Path dest = projectPath;

        try {
            Files.walk(src).forEach(source -> {
                try {
                    Path target = dest.resolve(src.relativize(source));
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
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

    // ------------------ Copy Project for Fixing ------------------
    public String copyProject(String originalPath) throws IOException {

        Path sourcePath = Paths.get(originalPath);

        // Determine fixed path safely (avoid _fixed duplication)
        String fixedPathStr = originalPath.endsWith("_fixed") ? originalPath : originalPath + "_fixed";
        Path fixedPath = Paths.get(fixedPathStr);

        // Delete old fixed folder if exists
        if (Files.exists(fixedPath)) {
            deleteDirectory(fixedPath);
        }

        // Copy project recursively
        Files.walk(sourcePath).forEach(source -> {
            try {
                Path target = fixedPath.resolve(sourcePath.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy file: " + source, e);
            }
        });

        return fixedPath.toString();
    }

    // ------------------ Helper: Delete directory recursively ------------------
    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path))
            return;

        Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // delete children first
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to delete: " + p, e);
                    }
                });
    }

    // METADATA WRITER

    public void registerProjectKey(String projectDir, String projectKey) {

        try {
            Path metadata = Paths.get(projectDir, ".project-key");
            Files.writeString(metadata, projectKey);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store project key", e);
        }
    }

}
