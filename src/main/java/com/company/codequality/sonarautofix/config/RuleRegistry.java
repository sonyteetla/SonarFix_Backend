package com.company.codequality.sonarautofix.config;

import com.company.codequality.sonarautofix.model.RuleConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RuleRegistry {

    private Map<String, RuleConfig> ruleMap;

    @PostConstruct
    public void loadRules() throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("rules.json");

        List<RuleConfig> rules =
                mapper.readValue(is, new TypeReference<>() {});

        ruleMap = rules.stream()
                .collect(Collectors.toMap(
                        RuleConfig::getRuleId,
                        r -> r
                ));
    }

    public RuleConfig getRule(String ruleId) {
        return ruleMap.get(ruleId);
    }
}