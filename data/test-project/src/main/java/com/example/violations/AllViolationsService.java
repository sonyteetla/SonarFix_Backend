package com.example.violations;

// ── Rule java:S1128 ─ Unused import ───────────────────────────────────────────
import java.util.HashMap; // VIOLATION: never used
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * A single class that deliberately violates all 24 SonarFix rules.
 * Each violation is labeled with its rule ID.
 */
@Service
public class AllViolationsService {

    // ── Rule java:S6833 / S3749 ─ Field injection instead of constructor ───────
    @Autowired
    private String someService; // VIOLATION: @Autowired field

    // ── Rule java:S1075 ─ Hardcoded URI / path ────────────────────────────────
    private static final String BASE_URL = "http://localhost:8080/api/data"; // VIOLATION

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S106 ─ System.out.println
    // ═══════════════════════════════════════════════════════════════════════════
    public void logSomething(String message) {
        System.out.println("Message: " + message); // VIOLATION: use a logger
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S108 ─ Empty catch block
    // ═══════════════════════════════════════════════════════════════════════════
    public void readConfig() {
        try {
            int result = Integer.parseInt("abc");
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
            report = report + item + ", "; // VIOLATION: use StringBuilder
        }
        return report;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S109 ─ Magic number
    // ═══════════════════════════════════════════════════════════════════════════
    public double calculateArea(double radius) {
        return 3.14159 * radius * radius; // VIOLATION: 3.14159 is a magic number
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1874 ─ Deprecated API
    // ═══════════════════════════════════════════════════════════════════════════
    @SuppressWarnings("deprecation")
    public String getDateString() {
        Date d = new Date(2024, 1, 1); // VIOLATION: deprecated constructor
        return d.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S2129 ─ Unnecessary String object creation
    // ═══════════════════════════════════════════════════════════════════════════
    public String unnecessaryNew() {
        String s = new String("hello world"); // VIOLATION: just use "hello world"
        return s.toUpperCase();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S112 ─ Throwing generic exception
    // ═══════════════════════════════════════════════════════════════════════════
    public void validateInput(String input) throws Exception {
        if (input == null) {
            throw new RuntimeException("Input is null"); // VIOLATION: too generic
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1192 ─ Duplicate string literals
    // ═══════════════════════════════════════════════════════════════════════════
    public void statusMessages() {
        String s1 = "ORDER_PENDING"; // VIOLATION: same literal repeated
        String s2 = "ORDER_PENDING"; // VIOLATION
        String s3 = "ORDER_PENDING"; // VIOLATION — extract to a constant
        System.out.println(s1 + s2 + s3);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S2095 ─ Resource not closed (should use try-with-resources)
    // ═══════════════════════════════════════════════════════════════════════════
    public int readFirstByte(String filePath) throws IOException {
        FileInputStream fis = new FileInputStream(filePath); // VIOLATION: never closed
        return fis.read();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S3655 ─ Optional.get() without check
    // ═══════════════════════════════════════════════════════════════════════════
    public String getUsername(Optional<String> user) {
        if (user.isPresent()) {
            return user.get(); // VIOLATION: use ifPresent/map/orElse
        }
        return "anonymous";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1481 ─ Unused local variable
    // ═══════════════════════════════════════════════════════════════════════════
    public int computeSum(int a, int b) {
        int unusedTemp = a * b; // VIOLATION: assigned but never used
        return a + b;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1854 ─ Dead store (value assigned but never read before overwrite)
    // ═══════════════════════════════════════════════════════════════════════════
    public int deadAssignment() {
        int value = 0; // VIOLATION: this assignment is overwritten
        value = 42; // immediately overwritten — first assign is dead
        return value;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1125 ─ Redundant boolean literal comparison
    // ═══════════════════════════════════════════════════════════════════════════
    public boolean isActive(boolean flag) {
        return flag == true; // VIOLATION: just return flag
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1698 ─ String comparison with == instead of .equals()
    // ═══════════════════════════════════════════════════════════════════════════
    public boolean isSameStatus(String a, String b) {
        return a == b; // VIOLATION: use a.equals(b)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1132 ─ Switch without default case
    // ═══════════════════════════════════════════════════════════════════════════
    public String getLabel(int code) {
        switch (code) {
            case 1:
                return "ACTIVE"; // VIOLATION: no default case
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
        Runnable task = new Runnable() { // VIOLATION: convert to lambda
            @Override
            public void run() {
                System.out.println("Task running");
            }
        };
        new Thread(task).start();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1612 ─ Lambda → Method reference
    // ═══════════════════════════════════════════════════════════════════════════
    public List<String> toUpper(List<String> names) {
        List<String> result = new ArrayList<>();
        names.forEach(n -> result.add(n.toUpperCase())); // VIOLATION: use method ref
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S1319 ─ Index-based for loop → enhanced for
    // ═══════════════════════════════════════════════════════════════════════════
    public void printAll(List<String> items) {
        for (int i = 0; i < items.size(); i++) { // VIOLATION: use for-each
            System.out.println(items.get(i));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule java:S3626 ─ Redundant conditional return (simplify to return expr)
    // ═══════════════════════════════════════════════════════════════════════════
    public boolean isPositive(int n) {
        if (n > 0) {
            return true; // VIOLATION: simplify to return n > 0
        } else {
            return false;
        }
    }
}
