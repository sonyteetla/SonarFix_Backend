package com.company.codequality.sonarautofix.repository;

import com.company.codequality.sonarautofix.model.ScanTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ScanRepository {
    private static final String STORAGE_PATH = "data/scans.json";
    private final Map<String, ScanTask> scanStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public ScanRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        loadScans();
    }

    public void save(ScanTask task) {
        scanStore.put(task.getScanId(), task);
        saveScans();
    }

    public ScanTask findById(String scanId) {
        return scanStore.get(scanId);
    }

    public List<ScanTask> findAll() {
        return new ArrayList<>(scanStore.values());
    }

    private synchronized void saveScans() {
        try {
            File file = new File(STORAGE_PATH);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            objectMapper.writeValue(file, scanStore);
        } catch (IOException e) {
            System.err.println("Failed to save scans: " + e.getMessage());
        }
    }

    private void loadScans() {
        try {
            File file = new File(STORAGE_PATH);
            if (file.exists()) {
                Map<String, ScanTask> loaded = objectMapper.readValue(file, new TypeReference<Map<String, ScanTask>>() {
                });
                scanStore.putAll(loaded);
                System.out.println("Loaded " + scanStore.size() + " scans from " + STORAGE_PATH);
            }
        } catch (IOException e) {
            System.err.println("Failed to load scans: " + e.getMessage());
        }
    }
}
