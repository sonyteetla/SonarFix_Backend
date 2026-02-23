package com.company.codequality.sonarautofix.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProjectUploadService {

    private static final String WORKSPACE = "C:/sonar-workspace/";

<<<<<<< HEAD
    // ------------------ ZIP Upload ------------------
=======
    //  ZIP Upload
>>>>>>> f194b4e29b65dfe498d0ef80187fe0192dad2ccc
    public String handleZipUpload(MultipartFile file) {
        try {
            String projectDir = WORKSPACE + System.currentTimeMillis();
            Files.createDirectories(Paths.get(projectDir));

            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    Path newFile = Paths.get(projectDir, entry.getName());

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

            return projectDir;

        } catch (Exception e) {
            throw new RuntimeException("ZIP Upload failed: " + e.getMessage(), e);
        }
    }

<<<<<<< HEAD
    // ------------------ GitHub Clone ------------------
=======
    //  GitHub Clone
>>>>>>> f194b4e29b65dfe498d0ef80187fe0192dad2ccc
    public String cloneGithub(String repoUrl) {
        try {
            String projectDir = WORKSPACE + System.currentTimeMillis();

            ProcessBuilder builder = new ProcessBuilder("git", "clone", repoUrl, projectDir);
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

<<<<<<< HEAD
    // ------------------ Local Directory ------------------
=======
    //  Local Directory
>>>>>>> f194b4e29b65dfe498d0ef80187fe0192dad2ccc
    public String useLocalDirectory(String localPath) {
        Path src = Paths.get(localPath);

        if (!Files.exists(src) || !Files.isDirectory(src)) {
            throw new RuntimeException("Invalid local directory");
        }

        String projectDir = WORKSPACE + System.currentTimeMillis();
        Path dest = Paths.get(projectDir);

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

<<<<<<< HEAD
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
        if (!Files.exists(path)) return;

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
=======
>>>>>>> f194b4e29b65dfe498d0ef80187fe0192dad2ccc
}