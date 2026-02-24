package com.company.codequality.sonarautofix.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FileContentResponse {

    private String filePath;
    private List<FileLine> lines;
}