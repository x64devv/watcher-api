package com.x64dev.watcher.models;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class LaravelLog {
    private LocalDateTime timestamp;
    private String level;
    private String message;
    private String context;
    private String stackTrace;
    private Map<String, String> additionalData;

    public LaravelLog() {
        this.additionalData = new HashMap<>();
    }

    // Getters and setters
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

    public Map<String, String> getAdditionalData() { return additionalData; }
    public void setAdditionalData(Map<String, String> additionalData) { this.additionalData = additionalData; }

    public void addAdditionalData(String key, String value) {
        this.additionalData.put(key, value);
    }

    @Override
    public String toString() {
        return String.format("{timestamp:%s, level:'%s', message:'%s', context:'%s', stackTrace:'%s', additionalData:%s}",
                timestamp, level, message, context, stackTrace, additionalData);
    }
}

