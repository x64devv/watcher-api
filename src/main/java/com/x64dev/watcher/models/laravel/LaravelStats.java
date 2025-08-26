package com.x64dev.watcher.models.laravel;

import lombok.Data;

@Data
public class LaravelStats {
    private final String type = "laravel_stats";
    private  int totalLogs;
    private int errorCount;
    private int warningCount;
    private int infoCount;
    private int debugCount;
}
