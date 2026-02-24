package com.company.codequality.sonarautofix.model;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FixRequest {

    private String filePath;
    private int line;
    private String fixType;
}