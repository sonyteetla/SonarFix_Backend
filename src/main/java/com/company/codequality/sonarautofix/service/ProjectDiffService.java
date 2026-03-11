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

            Files.walk(originalPath)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {

                        try {

                            String relative =
                                    originalPath.relativize(file).toString();

                            Path fixedFile =
                                    Paths.get(fixedDir, relative);

                            if (!Files.exists(fixedFile)) return;

                            List<String> originalLines =
                                    Files.readAllLines(file);

                            List<String> fixedLines =
                                    Files.readAllLines(fixedFile);

                            Patch<String> patch =
                                    DiffUtils.diff(originalLines, fixedLines);

                            if (patch.getDeltas().isEmpty()) return;

                            List<DiffLine> diffLines =
                                    generateDiffLines(originalLines, fixedLines, patch);

                            result.add(new FileDiff(relative, diffLines));

                        } catch (Exception e) {

                            System.err.println("Diff failed for file: " + file);
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

        // add remaining unchanged lines
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
}