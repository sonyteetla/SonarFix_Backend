package com.company.codequality.sonarautofix.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FileLine {

    private int lineNumber;

    // Instead of raw content
    private List<HighlightSegment> segments;

    private List<Issue> issues;
}