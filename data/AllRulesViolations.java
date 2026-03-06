package com.example.testproject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Arrays;

// ─── Unused import (java:S1128) ───────────────────────────────────────────────
import java.util.HashMap; // unused

public class AllRulesViolations {

    // ─── Field injection instead of constructor injection (java:S6833 / S3749) ──
    @org.springframework.beans.factory.annotation.Autowired
    private String injectedService;

    // ─── Utility class should have private constructor (java:S1118) ──────────────
    // (class itself is not a utility class, but HideUtility is triggered on utility
    // classes)

    // ─── Hardcoded path (java:S1075) ─────────────────────────────────────────────
    private static final String PATH = "C:/hardcoded/path/to/file.txt";

    // ─── Duplicate string literal (java:S1192)
    // ────────────────────────────────────
    public void duplicateStrings() {
        String a = "hello-world";
        String b = "hello-world";
        String c = "hello-world";
    }

    // ─── System.out.println (java:S106) ──────────────────────────────────────────
    public void printViolation() {
        System.out.println("This should use a logger"); // LINE 35
    }

    // ─── Empty catch block (java:S108) ───────────────────────────────────────────
    public void emptyCatch() {
        try {
            int x = 1 / 0;
        } catch (Exception e) {
            // empty // LINE 42
        }
    }

    // ─── String concatenation in loop (java:S1643) ───────────────────────────────
    public String stringInLoop(List<String> items) {
        String result = "";
        for (String item : items) {
            result = result + item; // LINE 50
        }
        return result;
    }

    // ─── Magic number (java:S109)
    // ─────────────────────────────────────────────────
    public double magicNumber(double radius) {
        return 3.14159 * radius * radius; // LINE 56
    }

    // ─── Deprecated API (java:S1874) ─────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    public Date deprecatedApi() {
        return new Date(2024, 1, 1); // LINE 62 — Date(int,int,int) deprecated
    }

    // ─── Unnecessary object creation (java:S2129)
    // ─────────────────────────────────
    public String unnecessaryNew() {
        String s = new String("hello"); // LINE 67
        return s;
    }

    // ─── Throwing generic exception (java:S112)
    // ───────────────────────────────────
    public void throwsGeneric() throws Exception {
        throw new RuntimeException("too generic"); // LINE 73
    }

    // ─── Resources not closed (java:S2095) ───────────────────────────────────────
    public void resourceNotClosed(String file) throws IOException {
        FileInputStream fis = new FileInputStream(file); // LINE 79
        int b = fis.read();
        // never closed!
    }

    // ─── Optional without check (java:S3655) ─────────────────────────────────────
    public String optionalUnsafe(Optional<String> opt) {
        if (opt.isPresent()) {
            return opt.get(); // LINE 87
        }
        return null;
    }

    // ─── Assignment in sub-expression (java:S1656) ───────────────────────────────
    public boolean assignInCondition(int[] arr) {
        int x;
        return (x = arr[0]) > 0; // LINE 94
    }

    // ─── Unused local variable (java:S1481) ──────────────────────────────────────
    public void unusedVar() {
        int unused = 42; // LINE 99
        System.out.println("done");
    }

    // ─── Dead store (java:S1854)
    // ──────────────────────────────────────────────────
    public int deadStore() {
        int result = 0;
        result = 5; // LINE 106 — first assignment never read
        return result;
    }

    // ─── Redundant boolean literal (java:S1125)
    // ───────────────────────────────────
    public boolean redundantBoolean(boolean flag) {
        return flag == true; // LINE 112
    }

    // ─── String comparison with == (java:S1698)
    // ───────────────────────────────────
    public boolean stringEquality(String a, String b) {
        return a == b; // LINE 117
    }

    // ─── Switch without default (java:S1132) ─────────────────────────────────────
    public String noDefaultSwitch(int x) {
        switch (x) {
            case 1:
                return "one"; // LINE 122
            case 2:
                return "two";
        }
        return null;
    }

    // ─── Anonymous class that should be lambda (java:S1604) ──────────────────────
    public void anonymousClass() {
        Runnable r = new Runnable() { // LINE 130
            public void run() {
                System.out.println("running");
            }
        };
        r.run();
    }

    // ─── Lambda that should be method reference (java:S1612) ─────────────────────
    public void lambdaToMethodRef(List<String> list) {
        list.stream()
                .map(s -> s.toUpperCase()) // LINE 140
                .collect(Collectors.toList());
    }

    // ─── Index for-loop → enhanced for (java:S1319) ──────────────────────────────
    public void indexForLoop(List<String> list) {
        for (int i = 0; i < list.size(); i++) { // LINE 145
            System.out.println(list.get(i));
        }
    }

    // ─── Redundant return (java:S3626) ───────────────────────────────────────────
    public boolean simplifyReturn(int x) {
        if (x > 0) {
            return true; // LINE 153
        } else {
            return false;
        }
    }
}
