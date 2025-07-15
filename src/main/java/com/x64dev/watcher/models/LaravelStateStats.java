package com.x64dev.watcher.models;

import lombok.Data;

import java.util.List;

@Data
public class LaravelStateStats {
    private int totalCount;
    private int errorsCount;
    private int warningsCount;
    private int infoCount;
    private int debugCount;
    private List<LaravelLog> logs;
}
