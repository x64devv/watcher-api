package com.x64dev.watcher.models.laravel;

import lombok.Data;

@Data
public class LaravelStats {
    private  int totalLogs;
    private int errorCount;
    private int warningCount;
    private int infoCount;
    private int debugCount;
}
