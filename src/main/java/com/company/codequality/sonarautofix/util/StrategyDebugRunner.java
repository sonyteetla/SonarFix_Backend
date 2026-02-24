package com.company.codequality.sonarautofix.util;

import com.company.codequality.sonarautofix.model.FixType;
import com.company.codequality.sonarautofix.strategy.FixStrategy;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class StrategyDebugRunner {

    private final List<FixStrategy> strategies;

    public StrategyDebugRunner(List<FixStrategy> strategies) {
        this.strategies = strategies;
    }

    @PostConstruct
    public void debugStrategies() {

        System.out.println("\n====== AutoFix Strategy Debug ======");

        Set<FixType> enumTypes = new HashSet<>(Arrays.asList(FixType.values()));
        Set<FixType> loadedTypes = new HashSet<>();

        System.out.println("Total FixTypes in ENUM: " + enumTypes.size());
        System.out.println("Total Strategies Loaded: " + strategies.size());
        System.out.println("\nLoaded Strategies:");

        for (FixStrategy strategy : strategies) {
            FixType type = strategy.getFixType();
            loadedTypes.add(type);

            System.out.println("✔ " + type +
                    " -> " + strategy.getClass().getSimpleName());
        }

        enumTypes.removeAll(loadedTypes);

        if (!enumTypes.isEmpty()) {
            System.out.println("\nMissing Strategies:");
            for (FixType missing : enumTypes) {
                System.out.println("❌ " + missing);
            }
        } else {
            System.out.println("\nNo Missing Strategies ✔");
        }

        System.out.println("====================================\n");
    }
}