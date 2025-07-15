package com.x64dev.watcher.service;

import com.x64dev.watcher.models.LaravelLog;
import com.x64dev.watcher.models.LogEventAdapter;
import com.x64dev.watcher.models.LogEventListener;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LaravelLogWatcher {
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\]\\s+(\\w+\\.\\w+):\\s+(.+)"
    );

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String logFilePath;
    private final List<LogEventListener> listeners;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;

    private WatchService watchService;
    private volatile boolean isWatching = false;
    private long lastFilePosition = 0;
    private String incompleteLogEntry = "";
    private final Object positionLock = new Object();

    public LaravelLogWatcher(String site) {
        this.logFilePath = System.getenv("SITES_BASE_URI") + "/" + site + "/laravel.log";
        this.listeners = new CopyOnWriteArrayList<>();
        this.executorService = Executors.newCachedThreadPool();
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
    }

    // Add listener
    public void addListener(LogEventListener listener) {
        listeners.add(listener);
    }

    // Remove listener
    public void removeListener(LogEventListener listener) {
        listeners.remove(listener);
    }

    public void removeListenerBySession(String sessionId) {
        for(LogEventListener listener : listeners){
            LogEventAdapter adapter = (LogEventAdapter) listener;
            if (adapter.getSession().getId().equals(sessionId)){
                listeners.remove(listener);
                return;
            }
        }
    }
    // Start watching the log file
    public void startWatching() throws IOException {
        if (isWatching) {
            return;
        }

        Path logPath = Paths.get(logFilePath);
        Path parentDir = logPath.getParent();

        if (parentDir == null) {
            throw new IOException("Cannot determine parent directory for: " + logFilePath);
        }

        // Create directory if it doesn't exist
        Files.createDirectories(parentDir);

        // Initialize file position
        initializeFilePosition();

        // Create watch service
        watchService = FileSystems.getDefault().newWatchService();
        parentDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

        isWatching = true;

        // Start file watcher thread
        executorService.submit(this::watchFileChanges);

        // Start periodic checker (fallback mechanism)
        scheduledExecutor.scheduleAtFixedRate(this::checkForNewContent, 1, 1, TimeUnit.SECONDS);

        // Notify listeners
        notifyListeners(listener -> listener.onFileWatchStarted(logFilePath));
    }

    // Stop watching
    public void stopWatching() {
        if (!isWatching) {
            return;
        }

        isWatching = false;

        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            notifyListeners(listener -> listener.onFileWatchError(e));
        }

        executorService.shutdown();
        scheduledExecutor.shutdown();

        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        notifyListeners(listener -> listener.onFileWatchStopped());
    }

    // Initialize file position to end of file
    private void initializeFilePosition() {
        try {
            File logFile = new File(logFilePath);
            if (logFile.exists()) {
                synchronized (positionLock) {
                    lastFilePosition = logFile.length();
                }
            }
        } catch (Exception e) {
            notifyListeners(listener -> listener.onFileWatchError(e));
        }
    }

    // Watch for file changes
    private void watchFileChanges() {
        try {
            while (isWatching) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    Path changedFile = (Path) event.context();
                    Path logPath = Paths.get(logFilePath);

                    if (changedFile.equals(logPath.getFileName())) {
                        processFileChange();
                    }
                }

                if (!key.reset()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            notifyListeners(listener -> listener.onFileWatchError(e));
        }
    }

    // Periodic check for new content (fallback)
    private void checkForNewContent() {
        if (isWatching) {
            processFileChange();
        }
    }

    // Process file changes
    private void processFileChange() {
        try {
            File logFile = new File(logFilePath);
            if (!logFile.exists()) {
                return;
            }

            long currentFileSize = logFile.length();

            synchronized (positionLock) {
                if (currentFileSize > lastFilePosition) {
                    readNewContent(currentFileSize);
                } else if (currentFileSize < lastFilePosition) {
                    // File was truncated or rotated
                    lastFilePosition = 0;
                    incompleteLogEntry = "";
                    readNewContent(currentFileSize);
                }
            }
        } catch (Exception e) {
            notifyListeners(listener -> listener.onFileWatchError(e));
        }
    }

    // Read new content from file
    private void readNewContent(long currentFileSize) throws IOException {
        synchronized (positionLock) {
            try (RandomAccessFile raf = new RandomAccessFile(logFilePath, "r")) {
                raf.seek(lastFilePosition);

                StringBuilder newContent = new StringBuilder();
                String line;

                while ((line = raf.readLine()) != null) {
                    newContent.append(line).append("\n");
                }

                if (newContent.length() > 0) {
                    String contentToProcess = incompleteLogEntry + newContent.toString();
                    List<LaravelLog> newEntries = parseNewLogEntries(contentToProcess);

                    if (!newEntries.isEmpty()) {
                        // Notify listeners
                        notifyListeners(listener -> listener.onLogEntriesAdded(newEntries));

                        // Notify for individual entries
                        for (LaravelLog entry : newEntries) {
                            notifyListeners(listener -> listener.onNewLogEntry(entry));
                        }
                    }
                }

                lastFilePosition = currentFileSize;
            }
        }
    }

    // Parse new log entries from content
    private List<LaravelLog> parseNewLogEntries(String content) {
        List<LaravelLog> entries = new ArrayList<>();
        String[] lines = content.split("\n");

        LaravelLog currentEntry = null;
        StringBuilder multiLineContent = new StringBuilder();
        StringBuilder remainingContent = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = LOG_PATTERN.matcher(line);

            if (matcher.find()) {
                // Save previous entry if exists
                if (currentEntry != null) {
                    finalizeLogEntry(currentEntry, multiLineContent.toString());
                    entries.add(currentEntry);
                }

                // Create new entry
                currentEntry = new LaravelLog();

                // Parse timestamp
                String timestampStr = matcher.group(1);
                currentEntry.setTimestamp(LocalDateTime.parse(timestampStr, DATE_FORMATTER));

                // Parse level
                String levelStr = matcher.group(2);
                String[] levelParts = levelStr.split("\\.");
                currentEntry.setLevel(levelParts.length > 1 ? levelParts[1] : levelStr);

                // Parse message
                String message = matcher.group(3);
                currentEntry.setMessage(message);

                // Reset multiline content
                multiLineContent = new StringBuilder();

            } else if (currentEntry != null) {
                // This is a continuation line
                multiLineContent.append(line).append("\n");
            } else {
                // This might be incomplete content from previous read
                remainingContent.append(line).append("\n");
            }
        }

        // Handle the last entry
        if (currentEntry != null) {
            // Check if this entry seems complete (next line doesn't start with timestamp)
            if (content.endsWith("\n") && !content.trim().isEmpty()) {
                finalizeLogEntry(currentEntry, multiLineContent.toString());
                entries.add(currentEntry);
                incompleteLogEntry = "";
            } else {
                // This entry might be incomplete, store it for next read
                incompleteLogEntry = reconstructLogEntry(currentEntry, multiLineContent.toString());
            }
        } else {
            // No new complete entries, store remaining content
            incompleteLogEntry = remainingContent.toString();
        }

        return entries;
    }

    // Reconstruct log entry as string for incomplete entries
    private String reconstructLogEntry(LaravelLog entry, String additionalContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(entry.getTimestamp().format(DATE_FORMATTER)).append("] ");
        sb.append("local.").append(entry.getLevel()).append(": ");
        sb.append(entry.getMessage()).append("\n");
        sb.append(additionalContent);
        return sb.toString();
    }

    // Finalize log entry (reused from original parser)
    private void finalizeLogEntry(LaravelLog entry, String additionalContent) {
        if (additionalContent.trim().isEmpty()) {
            return;
        }

        String[] lines = additionalContent.split("\n");
        StringBuilder contextBuilder = new StringBuilder();
        StringBuilder stackTraceBuilder = new StringBuilder();

        boolean inStackTrace = false;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

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

        parseAdditionalData(entry, contextBuilder.toString());
    }

    // Parse additional data (reused from original parser)
    private void parseAdditionalData(LaravelLog entry, String context) {
        Pattern keyValuePattern = Pattern.compile("(\\w+):\\s*([^\\n]+)");
        Matcher matcher = keyValuePattern.matcher(context);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2).trim();
            entry.addAdditionalData(key, value);
        }

        if (context.contains("{") && context.contains("}")) {
            try {
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

    // Notify all listeners
    private void notifyListeners(java.util.function.Consumer<LogEventListener> action) {
        for (LogEventListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        }
    }

    // Get current file position
    public long getCurrentFilePosition() {
        synchronized (positionLock) {
            return lastFilePosition;
        }
    }

    // Check if watcher is running
    public boolean isWatching() {
        return isWatching;
    }
}
