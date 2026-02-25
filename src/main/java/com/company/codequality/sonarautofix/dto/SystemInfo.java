package com.company.codequality.sonarautofix.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemInfo {
    private String version;
    private String os;
    private String javaVersion;
}
