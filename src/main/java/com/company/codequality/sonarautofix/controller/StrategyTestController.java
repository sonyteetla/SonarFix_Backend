package com.company.codequality.sonarautofix.controller;

import com.company.codequality.sonarautofix.model.FixType;
import com.company.codequality.sonarautofix.strategy.FixStrategy;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Debug controller to test every fix strategy against a real Java file.
 *
 * GET /api/debug/strategies → list all registered strategies
 * POST /api/debug/test-rule → test one specific rule on a file
 * POST /api/debug/test-all → test ALL rules on the violations sample file
 */
@RestController
@RequestMapping("/api/debug")
@CrossOrigin("*")
public class StrategyTestController {

    private static final Logger log = LoggerFactory.getLogger(StrategyTestController.class);

    private final List<FixStrategy> strategies;

    public StrategyTestController(List<FixStrategy> strategies) {
        this.strategies = strategies;
    }

    // ── 1. List all registered strategies ─────────────────────────────────────
    @GetMapping("/strategies")
    public ResponseEntity<?> listStrategies() {

        List<Map<String, String>> result = new ArrayList<>();

        Set<FixType> allTypes = new HashSet<>(Arrays.asList(FixType.values()));
        Set<FixType> loaded = new HashSet<>();

        for (FixStrategy s : strategies) {
            loaded.add(s.getFixType());
            result.add(Map.of(
                    "fixType", s.getFixType().name(),
                    "strategy", s.getClass().getSimpleName(),
                    "status", "LOADED"));
        }

        allTypes.removeAll(loaded);
        for (FixType missing : allTypes) {
            result.add(Map.of(
                    "fixType", missing.name(),
                    "strategy", "—",
                    "status", "MISSING"));
        }

        result.sort(Comparator.comparing(m -> m.get("status")));

        return ResponseEntity.ok(Map.of(
                "totalLoaded", loaded.size(),
                "totalMissing", allTypes.size(),
                "strategies", result));
    }

    // ── 2. Test a single rule on a specific file at a specific line ────────────
    @PostMapping("/test-rule")
    public ResponseEntity<?> testRule(
            @RequestParam(name = "filePath") String filePath,
            @RequestParam(name = "fixType") String fixType,
            @RequestParam(name = "line") int line) {

        FixStrategy strategy = strategies.stream()
                .filter(s -> s.getFixType().name().equalsIgnoreCase(fixType))
                .findFirst()
                .orElse(null);

        if (strategy == null) {
            return ResponseEntity.badRequest().body("No strategy found for: " + fixType);
        }

        try {
            Path path = Path.of(filePath);
            String original = Files.readString(path);
            CompilationUnit cu = StaticJavaParser.parse(path);

            boolean applied = strategy.apply(cu, line);
            String fixed = cu.toString();

            return ResponseEntity.ok(Map.of(
                    "fixType", fixType,
                    "line", line,
                    "applied", applied,
                    "original", original,
                    "fixed", applied ? fixed : "(no change)"));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("File error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Parse/fix error: " + e.getMessage());
        }
    }

    // ── 3. Test ALL rules on the violations sample file ────────────────────────
    @GetMapping("/test-all")
    public ResponseEntity<?> testAll(
            @RequestParam(name = "filePath", defaultValue = "data/AllRulesViolations.java") String filePath) {

        // Map: fixType → exact line in AllRulesViolations.java where violation exists
        Map<String, Integer> ruleLineMap = Map.ofEntries(
                Map.entry("REPLACE_SYSTEM_OUT", 38), // System.out.println
                Map.entry("REMOVE_UNUSED_IMPORT", 12), // import java.util.HashMap
                Map.entry("HANDLE_EMPTY_CATCH", 46), // empty catch
                Map.entry("STRING_BUILDER_LOOP", 54), // result = result + item
                Map.entry("EXTRACT_CONSTANT", 62), // 3.14159
                Map.entry("REPLACE_DEPRECATED_API", 68), // new Date(int,int,int)
                Map.entry("FIELD_TO_CONSTRUCTOR_INJECTION", 18), // @Autowired field
                Map.entry("OPTIMIZE_OBJECT_CREATION", 74), // new String("hello")
                Map.entry("REPLACE_GENERIC_EXCEPTION", 81), // throw new RuntimeException
                Map.entry("HIDE_UTILITY_CONSTRUCTOR", 1), // class level check
                Map.entry("EXTRACT_STRING_CONSTANT", 31), // "hello-world" x3
                Map.entry("SIMPLIFY_RETURN", 169), // if(x>0) return true; else return false
                Map.entry("CONVERT_TO_TRY_WITH_RESOURCES", 86), // FileInputStream not closed
                Map.entry("OPTIONAL_IF_PRESENT", 94), // opt.isPresent() + opt.get()
                Map.entry("SPLIT_ASSIGNMENT_FROM_CONDITION", 102), // (x = arr[0]) > 0
                Map.entry("REMOVE_UNUSED_VARIABLE", 107), // int unused = 42
                Map.entry("REMOVE_DEAD_ASSIGNMENT", 115), // result = 5 (overwritten)
                Map.entry("REMOVE_REDUNDANT_BOOLEAN", 122), // flag == true
                Map.entry("USE_EQUALS_FOR_STRING", 128), // a == b (String)
                Map.entry("ADD_DEFAULT_CASE", 135), // switch without default
                Map.entry("ANONYMOUS_TO_LAMBDA", 144), // new Runnable() { ... }
                Map.entry("LAMBDA_TO_METHOD_REFERENCE", 155), // s -> s.toUpperCase()
                Map.entry("FOR_TO_ENHANCED_FOR", 161), // for(int i=0; i<list.size(); i++)
                Map.entry("MOVE_TO_CONFIG", 27) // "C:/hardcoded/path..."
        );

        List<Map<String, Object>> results = new ArrayList<>();
        Path path = Path.of(filePath);

        if (!Files.exists(path)) {
            return ResponseEntity.badRequest().body(
                    "Test file not found: " + path.toAbsolutePath() +
                            "\nPlace AllRulesViolations.java in data/ directory.");
        }

        for (FixStrategy strategy : strategies) {
            String fixTypeName = strategy.getFixType().name();
            Integer targetLine = ruleLineMap.get(fixTypeName);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fixType", fixTypeName);
            result.put("strategy", strategy.getClass().getSimpleName());

            if (targetLine == null) {
                result.put("status", "SKIPPED");
                result.put("reason", "No test line defined");
                results.add(result);
                continue;
            }

            result.put("line", targetLine);

            try {
                // Re-parse fresh for each strategy (avoid cross-strategy contamination)
                CompilationUnit cu = StaticJavaParser.parse(path);
                boolean applied = strategy.apply(cu, targetLine);

                result.put("status", applied ? "✅ PASS" : "⚠ NO_MATCH");
                if (applied) {
                    // Show only the changed area (3 lines around target)
                    List<String> lines = Arrays.asList(cu.toString().split("\n"));
                    int start = Math.max(0, targetLine - 3);
                    int end = Math.min(lines.size(), targetLine + 3);
                    result.put("fixedSnippet", String.join("\n",
                            lines.subList(start, end)));
                }

            } catch (Exception e) {
                result.put("status", "❌ ERROR");
                result.put("error", e.getMessage());
            }

            results.add(result);
        }

        long pass = results.stream().filter(r -> "✅ PASS".equals(r.get("status"))).count();
        long noMatch = results.stream().filter(r -> "⚠ NO_MATCH".equals(r.get("status"))).count();
        long error = results.stream().filter(r -> "❌ ERROR".equals(r.get("status"))).count();

        return ResponseEntity.ok(Map.of(
                "summary", Map.of("pass", pass, "noMatch", noMatch, "error", error),
                "results", results));
    }
}
