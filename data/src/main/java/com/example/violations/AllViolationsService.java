package com.example.violations;

// ── Rule java:S1128 ─ Unused import ───────────────────────────────────────────
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single class that deliberately violates all 24 SonarFix rules.
 * Each violation is labeled with its rule ID.
 */
@Service
public class AllViolationsService {

    private static final Logger log = LoggerFactory.getLogger(AllViolationsService.class);

    // ── Rule java:S6833 / S3749 ─ Field injection instead of constructor ───────
    @Autowired
    private String // VIOLATION: @Autowired field
    someService;

    // ── Rule java:S1075 ─ Hardcoded URI / path ────────────────────────────────
    // VIOLATION
    private static final String BASE_URL = "http://localhost:8080/api/data";

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S106 ─ System.out.println
    // ═══════════════════════════════════════════════════════════════════════════
    public void logSomething(String message) {
        // VIOLATION: use a logger
        log.info("Message: {}", message);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S108 ─ Empty catch block
    // ═══════════════════════════════════════════════════════════════════════════
    public void readConfig() {
        try {
        } catch (NumberFormatException e) {
            // VIOLATION: empty catch block — swallowed silently
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1643 ─ String concatenation in loop
    // ═══════════════════════════════════════════════════════════════════════════
    public String buildReport(List<String> items) {
        String report = "";
        for (String item : items) {
            // VIOLATION: use StringBuilder
            report = report + item + ", ";
        }
        return report;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S109 ─ Magic number
    // ═══════════════════════════════════════════════════════════════════════════
    public double calculateArea(double radius) {
        // VIOLATION: 3.14159 is a magic number
        return 3.14159 * radius * radius;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1874 ─ Deprecated API
    // ═══════════════════════════════════════════════════════════════════════════
    @SuppressWarnings("deprecation")
    public String getDateString() {
        // VIOLATION: deprecated constructor
        Date d = new Date(2024, 1, 1);
        return d.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S2129 ─ Unnecessary String object creation
    // ═══════════════════════════════════════════════════════════════════════════
    public String unnecessaryNew() {
        // VIOLATION: just use "hello world"
        String s = new String("hello world");
        return s.toUpperCase();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S112 ─ Throwing generic exception
    // ═══════════════════════════════════════════════════════════════════════════
    public void validateInput(String input) throws Exception {
        if (input == null) {
            // VIOLATION: too generic
            throw new RuntimeException("Input is null");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1192 ─ Duplicate string literals
    // ═══════════════════════════════════════════════════════════════════════════
    public void statusMessages() {
        // VIOLATION: same literal repeated
        String s1 = ORDER_PENDING;
        // VIOLATION
        String s2 = "ORDER_PENDING";
        // VIOLATION — extract to a constant
        String s3 = "ORDER_PENDING";
        log.info("{}{}{}", s1, s2, s3);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S2095 ─ Resource not closed (should use try-with-resources)
    // ═══════════════════════════════════════════════════════════════════════════
    public int readFirstByte(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            return fis.read();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S3655 ─ Optional.get() without check
    // ═══════════════════════════════════════════════════════════════════════════
    public String getUsername(Optional<String> user) {
        if (user.isPresent()) {
            // VIOLATION: use ifPresent/map/orElse
            return user.get();
        }
        return "anonymous";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1481 ─ Unused local variable
    // ═══════════════════════════════════════════════════════════════════════════
    public int computeSum(int a, int b) {
        return a + b;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1854 ─ Dead store (value assigned but never read before overwrite)
    // ═══════════════════════════════════════════════════════════════════════════
    public int deadAssignment() {
        // VIOLATION: this assignment is overwritten
        int value = 0;
        // immediately overwritten — first assign is dead
        value = 42;
        return value;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1125 ─ Redundant boolean literal comparison
    // ═══════════════════════════════════════════════════════════════════════════
    public boolean isActive(boolean flag) {
        // VIOLATION: just return flag
        return flag;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1698 ─ String comparison with == instead of .equals()
    // ═══════════════════════════════════════════════════════════════════════════
    public boolean isSameStatus(String a, String b) {
        // VIOLATION: use a.equals(b)
        return a == b;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1132 ─ Switch without default case
    // ═══════════════════════════════════════════════════════════════════════════
    public String getLabel(int code) {
        switch(code) {
            case 1:
                // VIOLATION: no default case
                return "ACTIVE";
            case 2:
                return "INACTIVE";
            case 3:
                return "PENDING";
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1604 ─ Anonymous class → Lambda
    // ═══════════════════════════════════════════════════════════════════════════
    public void startTask() {
        Runnable task = () -> log.info("Task running");
        new Thread(task).start();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1612 ─ Lambda → Method reference
    // ═══════════════════════════════════════════════════════════════════════════
    public List<String> toUpper(List<String> names) {
        List<String> result = new ArrayList<>();
        // VIOLATION: use method ref
        names.forEach(n -> result.add(n.toUpperCase()));
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1319 ─ Index-based for loop → enhanced for
    // ═══════════════════════════════════════════════════════════════════════════
    public void printAll(List<String> items) {
        for (int i = 0; i < items.size(); i++) {
            // VIOLATION: use for-each
            log.info(items.get(i));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S3626 ─ Redundant conditional return (simplify to return expr)
    // ═══════════════════════════════════════════════════════════════════════════
    public boolean isPositive(int n) {
        if (n > 0) {
            // VIOLATION: simplify to return n > 0
            return true;
        } else {
            return false;
        }
    }

    private static final String ORDER_PENDING = "ORDER_PENDING";
}
