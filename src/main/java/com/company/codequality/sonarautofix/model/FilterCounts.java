package com.company.codequality.sonarautofix.model;

import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterCounts {

    private Map<String, Long> severityCounts;
    private Map<String, Long> qualityCounts;
    private Map<String, Long> ruleCounts;
}