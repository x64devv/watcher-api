package com.x64dev.watcher.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x64dev.watcher.models.laravel.LaravelLogEntry;
import com.x64dev.watcher.models.laravel.LaravelStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LaravelService {
    @Autowired
    ObjectMapper mapper;
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\]\\s+" +  // timestamp
                    "([^.]+)\\." +                                              // environment
                    "(\\w+):\\s+" +                                             // level
                    "([^{\\n]+?)" +                                             // message
                    "(?:\\s*(\\{.*?\\}))??" +                                   // context (optional)
                    "(?:\\s*(\\{.*\\}))?",                                      // stack trace (optional)
            Pattern.DOTALL
    );

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Parse a single log entry string
     */
    public static LaravelLogEntry parseLogEntry(String logEntry) {
        Matcher matcher = LOG_PATTERN.matcher(logEntry.trim());

        if (!matcher.find()) {
            return null; // Invalid log format
        }

        try {
            LocalDateTime timestamp = LocalDateTime.parse(matcher.group(1), DATETIME_FORMATTER);
            String environment = matcher.group(2);
            String level = matcher.group(3);
            String message = matcher.group(4).trim();
            String context = matcher.group(5);
            String stackTrace = matcher.group(6);

            return new LaravelLogEntry(timestamp, environment, level, message, context, stackTrace);
        } catch (Exception e) {
            System.err.println("Error parsing log entry: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse multiple log entries from a string
     */
    public static List<LaravelLogEntry> parseLogEntries(String logContent) {
        List<LaravelLogEntry> entries = new ArrayList<>();

        // Split by log entry pattern (looking for timestamp pattern at start of line)
        String[] lines = logContent.split("(?=\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\])");

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            LaravelLogEntry entry = parseLogEntry(line);
            if (entry != null) {
                entries.add(entry);
            }
        }

        return entries;
    }

    /**
     * Parse log entries from a file
     */
    public static List<LaravelLogEntry> parseLogFile(String filePath) throws IOException {
        try{
            StringBuilder content = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            return parseLogEntries(content.toString());
        }catch (IOException e){
            return new ArrayList<>();
        }
    }

    /**
     * Filter log entries by level
     */
    public static List<LaravelLogEntry> filterByLevel(List<LaravelLogEntry> entries, String level) {
        return entries.stream()
                .filter(entry -> entry.getLevel().equalsIgnoreCase(level))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * Filter log entries by environment
     */
    public static List<LaravelLogEntry> filterByEnvironment(List<LaravelLogEntry> entries, String environment) {
        return entries.stream()
                .filter(entry -> entry.getEnvironment().equalsIgnoreCase(environment))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public Map<String, String> fistLogsLoad(String siteFile){
        LaravelStats stats = new LaravelStats();
        Map<String, String> data = new HashMap<>();
        List<LaravelLogEntry> logs = new ArrayList<>();
        try{
            logs = parseLogFile(siteFile);
            stats.setTotalLogs(logs.size());
            stats.setErrorCount(filterByLevel(logs, "Error").size());
            stats.setWarningCount(filterByLevel(logs, "Warning").size());
            stats.setDebugCount(filterByLevel(logs, "Debug").size());
            stats.setInfoCount(filterByLevel(logs, "Info").size());
        }catch (IOException e){
            log.error("Failed to parse log file: ", e);
        }
        try{
            data.put("stats", mapper.writeValueAsString(stats));
            data.put("logs", mapper.writeValueAsString(logs));
        }catch (JsonProcessingException e){
            log.error("Failed to convert data to json: ", e);
        }
        return data;
    }
}
