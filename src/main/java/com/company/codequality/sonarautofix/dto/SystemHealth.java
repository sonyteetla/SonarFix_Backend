package com.company.codequality.sonarautofix.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemHealth {
    private String status;
    private boolean sonarConnectivity;
}
