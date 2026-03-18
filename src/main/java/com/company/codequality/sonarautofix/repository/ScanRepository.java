package com.company.codequality.sonarautofix.repository;

import com.company.codequality.sonarautofix.model.ScanTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ScanRepository {

    private static final String STORAGE_PATH = "data/scans.json";

    private final Map<String, ScanTask> scanStore = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public ScanRepository() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        loadScans();
    }

    /* ---------------- SAVE ---------------- */

    public synchronized void save(ScanTask task) {

        scanStore.put(task.getScanId(), task);
        persist();

    }

    /* ---------------- UPDATE ---------------- */

    public synchronized void update(ScanTask task) {

        if (!scanStore.containsKey(task.getScanId())) {
            throw new IllegalArgumentException("Scan not found: " + task.getScanId());
        }

        scanStore.put(task.getScanId(), task);

        persist();
    }

    /* ---------------- FIND ---------------- */

    public ScanTask findById(String scanId) {
        return scanStore.get(scanId);
    }

    public List<ScanTask> findAll() {
        return new ArrayList<>(scanStore.values());
    }

    public ScanTask findLatestByProjectKey(String projectKey) {
        return scanStore.values().stream()
                .filter(t -> projectKey.equals(t.getProjectKey()))
                .max(Comparator.comparing(ScanTask::getCreatedAt)) 
                .orElse(null);
    }

    /* ---------------- PERSIST ---------------- */

    private void persist() {

        try {

            File file = new File(STORAGE_PATH);

            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(file, scanStore);

        } catch (IOException e) {

            System.err.println("Failed to save scans.json");
            e.printStackTrace();
        }
    }

    /* ---------------- LOAD ---------------- */

    private void loadScans() {

        try {

            File file = new File(STORAGE_PATH);

            if (!file.exists()) {
                return;
            }

            Map<String, ScanTask> loaded =
                    objectMapper.readValue(
                            file,
                            new TypeReference<Map<String, ScanTask>>() {}
                    );

            scanStore.putAll(loaded);

            System.out.println(
                    "Loaded " + scanStore.size() + " scans"
            );

        } catch (Exception e) {

            System.out.println(
                    "⚠ scans.json corrupted. Resetting storage."
            );

            scanStore.clear();
        }
    }
    
    
}