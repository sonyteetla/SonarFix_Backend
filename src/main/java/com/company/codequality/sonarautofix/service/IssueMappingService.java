package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.config.RuleRegistry;
import com.company.codequality.sonarautofix.model.RuleConfig;
import com.company.codequality.sonarautofix.model.SonarIssue;
import com.company.codequality.sonarautofix.model.MappedIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
@Service
public class IssueMappingService {

    private final RuleRegistry ruleRegistry;

    public IssueMappingService(RuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    public List<MappedIssue> mapIssues(List<SonarIssue> sonarIssues) {

        List<MappedIssue> mappedIssues = new ArrayList<>();

        for (SonarIssue issue : sonarIssues) {

            String ruleId = issue.getRule();
            RuleConfig rule = ruleRegistry.getRule(ruleId);

            if (rule == null || !Boolean.TRUE.equals(rule.getEnabled())) {
                continue;
            }

            // FIX COMPONENT PATH
            String component = issue.getComponent();
            String filePath = component;

            int idx = component.indexOf(":");
            if (idx != -1) {
                filePath = component.substring(idx + 1);
            }

            MappedIssue mapped = new MappedIssue(
                    issue.getKey(),
                    ruleId,
                    filePath,
                    issue.getLine(),
                    rule.getTitle(),
                    rule.getSeverity(),
                    rule.getCategory(),
                    rule.isAutoFixable(),
                    rule.getFixType()
            );

            mappedIssues.add(mapped);
        }

        return mappedIssues;
    }
}