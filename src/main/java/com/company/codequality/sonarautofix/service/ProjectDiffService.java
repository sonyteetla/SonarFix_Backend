package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.model.DiffLine;
import com.company.codequality.sonarautofix.model.FileDiff;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.*;

@Service
public class ProjectDiffService {

    public List<FileDiff> compareProjects(String originalDir, String fixedDir) {

        List<FileDiff> result = new ArrayList<>();

        try {

            Path originalPath = Paths.get(originalDir);
            Map<String, String> renameMap = loadClassMapping(fixedDir);

            Files.walk(originalPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {

                        try {

                            String relative = originalPath.relativize(file).toString();
                            Path fixedFile = Paths.get(fixedDir, relative);

                            String oldName = file.getFileName().toString().replace(".java", "");

                            // 🔥 HANDLE RENAME
                            if (!Files.exists(fixedFile) && renameMap.containsKey(oldName)) {

                                String newName = renameMap.get(oldName);

                                fixedFile = Paths.get(fixedDir)
                                        .resolve(originalPath.relativize(file.getParent()))
                                        .resolve(newName + ".java");
                            }

                            if (!Files.exists(fixedFile)) return;

                            List<String> originalLines = Files.readAllLines(file);
                            List<String> fixedLines = Files.readAllLines(fixedFile);

                            Patch<String> patch = DiffUtils.diff(originalLines, fixedLines);

                            if (patch.getDeltas().isEmpty()) return;

                            List<DiffLine> diffLines =
                                    generateDiffLines(originalLines, fixedLines, patch);

                            // 🔥 update filename if renamed
                            String finalRelative = relative;
                            if (renameMap.containsKey(oldName)) {
                                String newName = renameMap.get(oldName);
                                finalRelative = relative.replace(oldName + ".java", newName + ".java");
                            }

                            result.add(new FileDiff(finalRelative, diffLines));

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

        } catch (Exception e) {
            throw new RuntimeException("Project diff failed", e);
        }

        return result;
    }

    private List<DiffLine> generateDiffLines(
            List<String> original,
            List<String> modified,
            Patch<String> patch) {

        List<DiffLine> lines = new ArrayList<>();

        int originalIndex = 0;
        int modifiedIndex = 0;

        for (AbstractDelta<String> delta : patch.getDeltas()) {

            int pos = delta.getSource().getPosition();

            while (originalIndex < pos) {

                lines.add(new DiffLine(
                        originalIndex + 1,
                        original.get(originalIndex),
                        modified.get(modifiedIndex),
                        "UNCHANGED"
                ));

                originalIndex++;
                modifiedIndex++;
            }

            List<String> src = delta.getSource().getLines();
            List<String> tgt = delta.getTarget().getLines();

            int max = Math.max(src.size(), tgt.size());

            for (int i = 0; i < max; i++) {

                String o = i < src.size() ? src.get(i) : null;
                String m = i < tgt.size() ? tgt.get(i) : null;

                String type;

                if (o != null && m != null)
                    type = "MODIFIED";
                else if (o != null)
                    type = "REMOVED";
                else
                    type = "ADDED";

                lines.add(new DiffLine(
                        originalIndex + i + 1,
                        o,
                        m,
                        type
                ));
            }

            originalIndex += src.size();
            modifiedIndex += tgt.size();
        }

        while (originalIndex < original.size()
                && modifiedIndex < modified.size()) {

            lines.add(new DiffLine(
                    originalIndex + 1,
                    original.get(originalIndex),
                    modified.get(modifiedIndex),
                    "UNCHANGED"
            ));

            originalIndex++;
            modifiedIndex++;
        }

        return lines;
    }

    private Map<String, String> loadClassMapping(String fixedDir) {

        Map<String, String> map = new HashMap<>();

        try {
            Path path = Paths.get(fixedDir, "class-mapping.csv");

            if (!Files.exists(path)) return map;

            List<String> lines = Files.readAllLines(path);

            for (String line : lines.stream().skip(1).toList()) {
                String[] p = line.split(",");
                if (p.length >= 2) {
                    map.put(p[0].trim(), p[1].trim());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return map;
    }
}