package com.company.codequality.sonarautofix.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleConfig {

    private String ruleId;
    private String title;
    private String description;
    private String severity;
    private String category;
    private Boolean enabled;
    private Boolean autofixSupported;   // from JSON
    private String fixType;

    // IMPORTANT — maps JSON autofixSupported → autoFixable
    public boolean isAutoFixable() {
        return Boolean.TRUE.equals(autofixSupported);
    }
}