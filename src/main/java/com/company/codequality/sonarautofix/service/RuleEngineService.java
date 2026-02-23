package com.company.codequality.sonarautofix.service;

import com.company.codequality.sonarautofix.config.RuleRegistry;
import com.company.codequality.sonarautofix.model.*;
import org.springframework.stereotype.Service;

@Service
public class RuleEngineService {

    private final RuleRegistry ruleRegistry;

    public RuleEngineService(RuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    public IssueResponse applyRuleMapping(IssueResponse response) {

        for (Issue issue : response.getIssues()) {

            RuleConfig rule =
                    ruleRegistry.getRule(issue.getRule());

            if (rule != null) {

                issue.setSupported(true);
                issue.setAutoFixable(rule.isAutoFixable());

                try {
                    issue.setFixType(
                            FixType.valueOf(rule.getFixType())
                    );
                } catch (Exception e) {
                    issue.setFixType(FixType.NONE);
                }

            } else {
                issue.setSupported(false);
                issue.setAutoFixable(false);
                issue.setFixType(FixType.NONE);
            }
        }

        return response;
    }
}