package com.x64dev.watcher.service;

import com.x64dev.watcher.models.LaravelLog;
import com.x64dev.watcher.models.LaravelLogParser;
import com.x64dev.watcher.models.LaravelStateStats;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.List;

@Service
@Slf4j
public class LaravelService {

    public LaravelStateStats loadStats(String selectedSite){
        String baseUri = "/home/x64dev/Desktop/logs/";
        var logs =  LaravelLogParser.parseLogFile(MessageFormat.format("{0}/{1}/laravel.log", baseUri, selectedSite));
        LaravelStateStats stats = new LaravelStateStats();
        stats.setTotalCount(logs.size());
        stats.setErrorsCount(logs.stream().filter((log)->log.getLevel().toLowerCase().equals("error")).toList().size());
        stats.setWarningsCount(logs.stream().filter((log)->log.getLevel().toLowerCase().equals("warning")).toList().size());
        stats.setInfoCount(logs.stream().filter((log)->log.getLevel().toLowerCase().equals("info")).toList().size());
        stats.setDebugCount(logs.stream().filter((log)->log.getLevel().toLowerCase().equals("info")).toList().size());
        stats.setLogs(logs);
        return stats;
    }

    private LaravelLog updatedLog(){
        var log = new LaravelLog();
        return log;
    }


}
