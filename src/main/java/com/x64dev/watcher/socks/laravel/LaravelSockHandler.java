package com.x64dev.watcher.socks.laravel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x64dev.watcher.models.LaravelLog;
import com.x64dev.watcher.models.LogEventAdapter;
import com.x64dev.watcher.models.LogEventListener;
import com.x64dev.watcher.service.LaravelLogWatcher;
import com.x64dev.watcher.service.LaravelService;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
public class LaravelSockHandler extends TextWebSocketHandler {
    @Autowired
    LaravelService laravelService;

    @Getter
    @Autowired
    private static LaravelSockHandler instance;

    @Autowired
    private  ObjectMapper mapper;


    @PostConstruct
    private void init() {
        String site = System.getenv("DEFAULT_SITE");
        if(!fileWatchers.containsKey(site)){
            LaravelLogWatcher watcher = new LaravelLogWatcher(site);
            try{
                watcher.startWatching();
            }catch (IOException e){
                log.error("Failed to start watcher : {}", e.getMessage(), e);
            }
            fileWatchers.put(System.getenv("DEFAULT_SITE"), watcher);
        }
        instance = this;
    }

    private final Map<String, LaravelLogWatcher> fileWatchers = new ConcurrentHashMap<>();
    private final Map<String, String> sessionSite = new ConcurrentHashMap<>();
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String site = System.getenv("DEFAULT_SITE");
        sessionSite.put(session.getId(), site);
        LaravelLogWatcher watcher = fileWatchers.get(site);
        watcher.addListener(newListener(session));
        var stats = laravelService.loadStats(site);
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(stats)));
        }catch (IOException e){
            log.error("Failed to send data to lara-sock on connection: {}", e.getMessage(), e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        MessageBody body = mapper.readValue(message.getPayload(), MessageBody.class);
        if(!fileWatchers.containsKey(body.getSite())){
            try{
                LaravelLogWatcher watcher = new LaravelLogWatcher(body.getSite());
                watcher.startWatching();
                fileWatchers.put(body.getSite(), watcher);
            }catch (IOException e){
                log.error("Failed to start watcher : {}", e.getMessage(), e);
            }
        }

        LaravelLogWatcher watcher = fileWatchers.get(body.getSite());
        watcher.addListener(newListener(session));

        log.info("New site received: {}", body.getSite());
        var stats = laravelService.loadStats(body.getSite());

        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(stats)));
        }catch (IOException e){
            log.error("Failed to send data to lara-sock on connection: {}", e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        LaravelLogWatcher watcher = fileWatchers.get(sessionSite.get(session.getId()));
        watcher.removeListenerBySession(session.getId());
    }

    private LogEventListener newListener(WebSocketSession session){
        return new LogEventAdapter(session) {
            @Override
            public void onNewLogEntry(LaravelLog logEntry) {
                log.info("==== Trying on new log entry");
                try{
                    var msg =new HashMap <String, String>();
                    msg.put("type", "update");
                    msg.put("mode", "single");
                    msg.put("data", logEntry.toString());
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
                } catch (IOException e) {
                    log.error("Failed to send message to lara-sock: {}", e.getMessage(), e);
                }
            }

            @Override
            public void onLogEntriesAdded(List<LaravelLog> logEntries) {
                /*try{
                    var msg =new HashMap <String, String>();
                    msg.put("type", "update");
                    msg.put("mode", "multiple");
                    msg.put("data", mapper.writeValueAsString(logEntries));
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
                } catch (IOException e) {
                    log.error("Failed to send message to lara-sock: {}", e.getMessage(), e);
                }
                 */
            }
        };
    }
}

@Data
class MessageBody {
    private String site;
}
