package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class MemoryService {

    private List<String> memory = new ArrayList<>();

    public void save(String message) {
        memory.add(message);
    }

    public String getContext() {
        return String.join("\n", memory);
    }

    public void clear() {
        memory.clear();
    }
}