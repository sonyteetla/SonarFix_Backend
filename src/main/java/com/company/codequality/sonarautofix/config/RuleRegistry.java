package com.company.codequality.sonarautofix.config;

import com.company.codequality.sonarautofix.model.RuleConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(RuleRegistry.class);

    private Map<String, RuleConfig> ruleMap;

    @PostConstruct
    public void loadRules() throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("rules.json");

        if (is == null) {
            throw new RuntimeException("rules.json NOT FOUND in resources folder");
        }

        List<RuleConfig> rules = mapper.readValue(is, new TypeReference<List<RuleConfig>>() {
        });

        ruleMap = rules.stream()
                .collect(Collectors.toMap(
                        RuleConfig::getRuleId,
                        r -> r));

        log.info("Loaded {} rule(s) from rules.json", ruleMap.size());
    }

    public RuleConfig getRule(String ruleId) {
        return ruleMap.get(ruleId);
    }

    public Map<String, RuleConfig> getAllRules() {
        return ruleMap;
    }
}
