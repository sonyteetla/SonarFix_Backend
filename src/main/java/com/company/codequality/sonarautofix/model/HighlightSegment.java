package com.company.codequality.sonarautofix.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HighlightSegment {

    private String text;
    private boolean highlighted;
}