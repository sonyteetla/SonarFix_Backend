package com.company.codequality.sonarautofix.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleCountInfo {
    private long count;
    private boolean autoFixable;
}
