package com.x64dev.watcher.models.laravel;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LaravelLogEntry {
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    private String environment;
    private String level;
    private String message;
    private String context;
    private String stackTrace;

    public LaravelLogEntry(LocalDateTime timestamp, String environment, String level,
                           String message, String context, String stackTrace) {
        this.timestamp = timestamp;
        this.environment = environment;
        this.level = level;
        this.message = message;
        this.context = context;
        this.stackTrace = stackTrace;
    }
}
