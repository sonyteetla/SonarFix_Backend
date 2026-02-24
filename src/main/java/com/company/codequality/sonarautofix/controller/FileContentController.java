package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.*;
import com.company.codequality.sonarautofix.service.FileContentService;
import com.company.codequality.sonarautofix.service.ScanIssueService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileContentController {

    private final FileContentService fileContentService;
    private final ScanIssueService scanIssueService;

    public FileContentController(FileContentService fileContentService,
                                 ScanIssueService scanIssueService) {
        this.fileContentService = fileContentService;
        this.scanIssueService = scanIssueService;
    }

    @GetMapping("/{projectKey}")
    public FileContentResponse getFileContent(
            @PathVariable String projectKey,
            @RequestParam String filePath) {

        List<String> rawLines =
                fileContentService.getFileContent(projectKey, filePath);

        List<Issue> allIssues =
                scanIssueService.fetchAllIssues(projectKey, null, null, null);

        List<Issue> issuesForFile =
                allIssues.stream()
                        .filter(issue -> filePath.equals(issue.getFilePath()))
                        .toList();

        List<FileLine> fileLines = new ArrayList<>();

        for (int i = 0; i < rawLines.size(); i++) {

            final int lineNumber = i + 1;
            final String lineContent = rawLines.get(i);

            List<Issue> lineIssues =
                    issuesForFile.stream()
                            .filter(issue ->
                                    issue.getStartLine() != null &&
                                    issue.getStartLine() == lineNumber)
                            .sorted(Comparator.comparing(Issue::getStartOffset))
                            .toList();

            List<HighlightSegment> segments = new ArrayList<>();

            if (lineIssues.isEmpty()) {

                segments.add(
                        HighlightSegment.builder()
                                .text(lineContent)
                                .highlighted(false)
                                .build()
                );

            } else {

                int currentIndex = 0;

                for (Issue issue : lineIssues) {

                    int start = issue.getStartOffset() != null ? issue.getStartOffset() : 0;
                    int end = issue.getEndOffset() != null ? issue.getEndOffset() : 0;

                    if (start > currentIndex) {
                        segments.add(
                                HighlightSegment.builder()
                                        .text(lineContent.substring(currentIndex, start))
                                        .highlighted(false)
                                        .build()
                        );
                    }

                    segments.add(
                            HighlightSegment.builder()
                                    .text(lineContent.substring(start, end))
                                    .highlighted(true)
                                    .build()
                    );

                    currentIndex = end;
                }

                if (currentIndex < lineContent.length()) {
                    segments.add(
                            HighlightSegment.builder()
                                    .text(lineContent.substring(currentIndex))
                                    .highlighted(false)
                                    .build()
                    );
                }
            }

            fileLines.add(
                    FileLine.builder()
                            .lineNumber(lineNumber)
                            .segments(segments)
                            .issues(lineIssues)
                            .build()
            );
        }

        return FileContentResponse.builder()
                .filePath(filePath)
                .lines(fileLines)
                .build();
    }
}