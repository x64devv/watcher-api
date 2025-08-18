package com.x64dev.watcher.models.laravel;

public interface LaravelLogListener {
    void onLogEntry(LaravelLogEntry entry);
    void onError(Exception error);
}
