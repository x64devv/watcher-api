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
import java.util.*;
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
                    "(.*?)(?=(?:\\n\\[\\d{4}-\\d{2}-\\d{2})|$)", // capture everything until next log entry or end
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
            String fullContent = matcher.group(4).trim();

            // Parse message, context, and stack trace from the full content
            String message = fullContent;
            String context = null;
            String stackTrace = null;

            // Look for JSON content in the full content
            int jsonStart = findJsonStart(fullContent);
            if (jsonStart != -1) {
                String beforeJson = fullContent.substring(0, jsonStart).trim();
                String jsonPart = extractCompleteJson(fullContent, jsonStart);

                if (jsonPart != null && isValidJson(jsonPart)) {
                    message = beforeJson;
                    context = jsonPart;

                    // Check if there's content after the JSON (potential stack trace)
                    int jsonEnd = jsonStart + jsonPart.length();
                    if (jsonEnd < fullContent.length()) {
                        String afterJson = fullContent.substring(jsonEnd).trim();
                        if (!afterJson.isEmpty()) {
                            stackTrace = afterJson;
                        }
                    }
                }
            }

            // Clean up the message - remove extra whitespace and newlines but preserve structure
            if (message != null && !message.isEmpty()) {
                message = message.replaceAll("\\n+", " ").replaceAll("\\s+", " ").trim();
            }

            return new LaravelLogEntry(timestamp, environment, level, message, context, stackTrace);
        } catch (Exception e) {
            System.err.println("Error parsing log entry: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract a complete JSON object starting from the given position
     */
    private static String extractCompleteJson(String text, int startPos) {
        int braceCount = 0;
        int i = startPos;

        // Find the opening brace
        while (i < text.length() && text.charAt(i) != '{') {
            i++;
        }

        if (i >= text.length()) {
            return null;
        }

        int jsonStart = i;

        // Count braces to find the complete JSON object
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    // Found complete JSON object
                    return text.substring(jsonStart, i + 1);
                }
            }
        }

        return null; // Incomplete JSON
    }

    /**
     * Find the start position of a JSON object in the string
     */
    private static int findJsonStart(String text) {
        // Look for JSON pattern - typically starts with whitespace followed by '{'
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '{') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if a string is valid JSON
     */
    private static boolean isValidJson(String jsonString) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse multiple log entries from a string - improved version
     */
    public static List<LaravelLogEntry> parseLogEntries(String logContent) {
        List<LaravelLogEntry> entries = new ArrayList<>();

        // Split by timestamp pattern at the beginning of lines
        Pattern splitPattern = Pattern.compile("(?=^\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\])", Pattern.MULTILINE);
        String[] logBlocks = splitPattern.split(logContent);

        for (String block : logBlocks) {
            if (block.trim().isEmpty()) continue;

            LaravelLogEntry entry = parseLogEntry(block.trim());
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
        try {
            StringBuilder content = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            return parseLogEntries(content.toString());
        } catch (IOException e) {
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

    public Map<String, Object> fistLogsLoad(String siteFile) {
        LaravelStats stats = new LaravelStats();
        Map<String, Object> data = new HashMap<>();
        List<LaravelLogEntry> logs = new ArrayList<>();
        try {
            logs = parseLogFile(siteFile);
            stats.setTotalLogs(logs.size());
            stats.setErrorCount(filterByLevel(logs, "ERROR").size());
            stats.setWarningCount(filterByLevel(logs, "WARNING").size());
            stats.setDebugCount(filterByLevel(logs, "DEBUG").size());
            stats.setInfoCount(filterByLevel(logs, "INFO").size());
        } catch (IOException e) {
            // Assuming you have a logger instance
            System.err.println("Failed to parse log file: " + e.getMessage());
        }

        data.put("stats", stats);
        data.put("logs", logs);
        return data;
    }
}