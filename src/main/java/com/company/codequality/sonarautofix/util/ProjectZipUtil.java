package com.company.codequality.sonarautofix.util;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ProjectZipUtil {

    public static String zipProject(String sourceDirPath) {

        String zipFilePath = sourceDirPath + "-refactored.zip";

        try (ZipOutputStream zs =
                     new ZipOutputStream(
                             new FileOutputStream(zipFilePath))) {

            Path sourcePath = Paths.get(sourceDirPath);

            Files.walk(sourcePath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {

                        ZipEntry zipEntry =
                                new ZipEntry(sourcePath.relativize(path).toString());

                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });

        } catch (IOException e) {
            throw new RuntimeException("Failed to zip project", e);
        }

        return zipFilePath;
    }
}