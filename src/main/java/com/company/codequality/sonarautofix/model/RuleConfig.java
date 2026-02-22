package com.company.codequality.sonarautofix.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleConfig {

    private String ruleId;
    private String title;
    private boolean autoFixable;
    private String fixType;

}