package com.company.codequality.sonarautofix.model;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FixRequest {

    private String filePath;
    private Integer line;
    private String fixType;
}