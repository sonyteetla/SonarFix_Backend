package com.company.codequality.sonarautofix.repository;

import com.company.codequality.sonarautofix.model.FixRecord;
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
public class FixRecordRepository {
    private static final String STORAGE_PATH = "data/fixes.json";
    private final Map<String, FixRecord> fixHistory = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public FixRecordRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        loadFixes();
    }

    public FixRecord save(FixRecord record) {
        if (record.getId() == null) {
            record.setId(java.util.UUID.randomUUID().toString());
        }
        fixHistory.put(record.getId(), record);
        saveFixes();
        return record;
    }

    public List<FixRecord> findAll() {
        return new ArrayList<>(fixHistory.values());
    }

    public long count() {
        return fixHistory.size();
    }

    private synchronized void saveFixes() {
        try {
            File file = new File(STORAGE_PATH);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            objectMapper.writeValue(file, fixHistory);
        } catch (IOException e) {
            System.err.println("Failed to save fix records: " + e.getMessage());
        }
    }

    private void loadFixes() {
        try {
            File file = new File(STORAGE_PATH);
            if (file.exists()) {
                Map<String, FixRecord> loaded = objectMapper.readValue(file,
                        new TypeReference<Map<String, FixRecord>>() {
                        });
                fixHistory.putAll(loaded);
                System.out.println("Loaded " + fixHistory.size() + " fix records from " + STORAGE_PATH);
            }
        } catch (IOException e) {
            System.err.println("Failed to load fix records: " + e.getMessage());
        }
    }
}
