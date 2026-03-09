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

            // Get rule from your custom rule registry
            RuleConfig rule = ruleRegistry.getRule(issue.getRule());

            if (rule == null) {
                continue; // Skip rules not in your custom rules.json
            }

            // Create mapped issue using your 8-field constructor
            MappedIssue mapped = new MappedIssue(
                    issue.getKey(),            // key
                    issue.getRule(),           // ruleId
                    issue.getComponent(),      // file
                    issue.getLine(),           // line
                    rule.getTitle(),           // title
                    rule.getSeverity(),        // severity
                    rule.getCategory(),        // category
                    rule.isAutoFixable(),     // autoFixable
                    rule.getFixType()
            );

            mappedIssues.add(mapped);
        }

        return mappedIssues;
    }
}
