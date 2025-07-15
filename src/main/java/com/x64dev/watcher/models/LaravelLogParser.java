package com.x64dev.watcher.models;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LaravelLogParser {


    // Pattern to match Laravel log format: [2023-12-01 10:30:45] local.ERROR: Message
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\]\\s+(\\w+\\.\\w+):\\s+(.+)"
    );

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(LaravelLogParser.class);

    /**
     * Parse Laravel log file and return list of log entries
     */
    public static List<LaravelLog> parseLogFile(String filePath){
        List<LaravelLog> logEntries = new ArrayList<>();
        try{
            List<String> lines = Files.readAllLines(Paths.get(filePath));

            LaravelLog currentEntry = null;
            StringBuilder multiLineContent = new StringBuilder();

            for (String line : lines) {
                Matcher matcher = LOG_PATTERN.matcher(line);

                if (matcher.find()) {
                    // Save previous entry if exists
                    if (currentEntry != null) {
                        finalizeLogEntry(currentEntry, multiLineContent.toString());
                        logEntries.add(currentEntry);
                    }

                    // Create new entry
                    currentEntry = new LaravelLog();

                    // Parse timestamp
                    String timestampStr = matcher.group(1);
                    currentEntry.setTimestamp(LocalDateTime.parse(timestampStr, DATE_FORMATTER));

                    // Parse level (e.g., "local.ERROR" -> "ERROR")
                    String levelStr = matcher.group(2);
                    String[] levelParts = levelStr.split("\\.");
                    currentEntry.setLevel(levelParts.length > 1 ? levelParts[1] : levelStr);

                    // Parse message
                    String message = matcher.group(3);
                    currentEntry.setMessage(message);

                    // Reset multiline content
                    multiLineContent = new StringBuilder();

                } else if (currentEntry != null) {
                    // This is a continuation line (stack trace, context, etc.)
                    multiLineContent.append(line).append("\n");
                }
            }

            // Don't forget the last entry
            if (currentEntry != null) {
                finalizeLogEntry(currentEntry, multiLineContent.toString());
                logEntries.add(currentEntry);
            }

        }catch (IOException e){
            log.error("Failed to parse the files: {}", e.getMessage());
        }
        return logEntries;
    }

    /**
     * Finalize log entry by parsing additional content
     */
    private static void finalizeLogEntry(LaravelLog entry, String additionalContent) {
        if (additionalContent.trim().isEmpty()) {
            return;
        }

        // Split content to find context and stack trace
        String[] lines = additionalContent.split("\n");
        StringBuilder contextBuilder = new StringBuilder();
        StringBuilder stackTraceBuilder = new StringBuilder();

        boolean inStackTrace = false;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            // Check if this line starts a stack trace
            if (line.contains("Stack trace:") || line.contains("#0 ") || line.matches("^#\\d+.*")) {
                inStackTrace = true;
            }

            if (inStackTrace) {
                stackTraceBuilder.append(line).append("\n");
            } else {
                contextBuilder.append(line).append("\n");
            }
        }

        entry.setContext(contextBuilder.toString().trim());
        entry.setStackTrace(stackTraceBuilder.toString().trim());

        // Parse additional structured data (if any)
        parseAdditionalData(entry, contextBuilder.toString());
    }

    /**
     * Parse additional structured data from context
     */
    private static void parseAdditionalData(LaravelLog entry, String context) {
        // Look for key-value pairs in the context
        Pattern keyValuePattern = Pattern.compile("(\\w+):\\s*([^\\n]+)");
        Matcher matcher = keyValuePattern.matcher(context);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2).trim();
            entry.addAdditionalData(key, value);
        }

        // Parse JSON-like structures if present
        if (context.contains("{") && context.contains("}")) {
            try {
                // Extract JSON-like content (basic parsing)
                int startBrace = context.indexOf('{');
                int endBrace = context.lastIndexOf('}');
                if (startBrace != -1 && endBrace != -1 && endBrace > startBrace) {
                    String jsonLike = context.substring(startBrace, endBrace + 1);
                    entry.addAdditionalData("json_data", jsonLike);
                }
            } catch (Exception e) {
                // Ignore JSON parsing errors
            }
        }
    }

    /**
     * Filter log entries by level
     */
    public static List<LaravelLog> filterByLevel(List<LaravelLog> entries, String level) {
        return entries.stream()
                .filter(entry -> entry.getLevel().equalsIgnoreCase(level))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * Filter log entries by date range
     */
    public static List<LaravelLog> filterByDateRange(List<LaravelLog> entries,
                                                     LocalDateTime startDate,
                                                     LocalDateTime endDate) {
        return entries.stream()
                .filter(entry -> {
                    LocalDateTime timestamp = entry.getTimestamp();
                    return (startDate == null || timestamp.isAfter(startDate)) &&
                            (endDate == null || timestamp.isBefore(endDate));
                })
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * Search log entries by message content
     */
    public static List<LaravelLog> searchByMessage(List<LaravelLog> entries, String searchTerm) {
        return entries.stream()
                .filter(entry -> entry.getMessage().toLowerCase().contains(searchTerm.toLowerCase()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

}
