package com.x64dev.watcher.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

@Slf4j
public abstract class LogEventAdapter implements LogEventListener {

    @Getter
    private final WebSocketSession session;
    protected LogEventAdapter(WebSocketSession session){
       this.session  = session;
    }
    @Override
    public void onNewLogEntry(LaravelLog logEntry) {
    }

    @Override
    public void onLogEntriesAdded(List<LaravelLog> logEntries) {
    }

    @Override
    public void onFileWatchError(Exception error) {

    }

    @Override
    public void onFileWatchStarted(String filePath) {

    }

    @Override
    public void onFileWatchStopped() {

    }
}
