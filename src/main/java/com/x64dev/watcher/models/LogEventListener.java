package com.x64dev.watcher.models;

import java.util.List;

public interface LogEventListener {
    void onNewLogEntry(LaravelLog logEntry);
    void onLogEntriesAdded(List<LaravelLog> logEntries);
    void onFileWatchError(Exception error);
    void onFileWatchStarted(String filePath);
    void onFileWatchStopped();
}
