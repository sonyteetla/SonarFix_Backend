package com.company.codequality.sonarautofix.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.company.codequality.sonarautofix.service.ProjectUploadService;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProjectUploadService {

    private static final String WORKSPACE = "D:/sonar-workspace/";

    //  ZIP Upload
    public String handleZipUpload(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Uploaded file is empty");
            }

            String projectDir = WORKSPACE + System.currentTimeMillis();
            Files.createDirectories(Paths.get(projectDir));

            ZipInputStream zis = new ZipInputStream(file.getInputStream());
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                File newFile = new File(projectDir, entry.getName());

                // Prevent Zip Slip
                String canonicalPath = newFile.getCanonicalPath();
                if (!canonicalPath.startsWith(new File(projectDir).getCanonicalPath())) {
                    throw new IOException("Invalid ZIP entry");
                }

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
            zis.close();

            return projectDir;

        } catch (Exception e) {
            throw new RuntimeException("ZIP Upload failed: " + e.getMessage());
        }
    }

    //  GitHub Clone
    public String cloneGithub(String repoUrl) {
        try {
            String projectDir = WORKSPACE + System.currentTimeMillis();

            ProcessBuilder builder = new ProcessBuilder(
                    "git", "clone", repoUrl, projectDir
            );

            builder.redirectErrorStream(true);
            Process process = builder.start();

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            StringBuilder output = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException("Git clone failed:\n" + output);
            }

            return projectDir;

        } catch (Exception e) {
            throw new RuntimeException("Git clone failed: " + e.getMessage());
        }
    }

    //  Local Directory
    public String useLocalDirectory(String localPath) {

        File src = new File(localPath);

        if (!src.exists() || !src.isDirectory()) {
            throw new RuntimeException("Invalid local directory");
        }

        String projectDir = WORKSPACE + System.currentTimeMillis();

        try {
            Files.createDirectories(Paths.get(projectDir));

            Files.walk(Paths.get(localPath))
                .forEach(source -> {
                    Path destination = Paths.get(projectDir,
                            source.toString().substring(localPath.length()));

                    try {
                        if (source.toFile().isDirectory()) {
                            Files.createDirectories(destination);
                        } else {
                            Files.copy(source, destination,
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

            return projectDir;

        } catch (Exception e) {
            throw new RuntimeException("Local directory copy failed: " + e.getMessage());
        }
    }

}
