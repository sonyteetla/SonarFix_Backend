package com.company.codequality.sonarautofix.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixRecord {
    private String id;
    private String projectKey;
    private String filePath;
    private int line;
    private String fixType;
    private LocalDateTime fixedAt;
}
