package com.x64dev.watcher.socks.laravel;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.x64dev.watcher.models.laravel.LaravelLogListener;
import com.x64dev.watcher.models.laravel.LaravelLogWatcher;
import com.x64dev.watcher.models.laravel.LaravelSessionLogListener;
import com.x64dev.watcher.service.LaravelService;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class LaravelSockHandler extends TextWebSocketHandler {

    @Autowired
    LaravelService laravelService;
    ConcurrentHashMap<String, LaravelLogWatcher> watchers  = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, List<WebSocketSession>> listenParties = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_OPTIONALS);

        String msg = (String) message.getPayload();
        LaravelMessage msgJson = mapper.readValue(msg, LaravelMessage.class);
        Map<String, Object> data = laravelService.fistLogsLoad(msgJson.data.get("file"));

        try{
            Map<String, Object> statsMsg = new HashMap<>();
            statsMsg.put("type", "laravel_stats");
            statsMsg.put("stats", data.get("stats"));
            session.sendMessage(new TextMessage(mapper.writeValueAsString(data.get("stats"))));
        }catch (IOException e){
            log.error("Failed to send stats message: ", e);
        }

        try{
            Map<String, Object>  logsMsg = new HashMap<>();
            logsMsg.put("type", "all_logs");
            logsMsg.put("logs", data.get("logs"));
            session.sendMessage(new TextMessage(mapper.writeValueAsString(logsMsg)));
        }catch (IOException e){
            log.error("Failed to send logs: ", e);
        }
        manageWatcher(msgJson.data.get("file"), session);
    }



    private void manageWatcher(String siteFile, WebSocketSession session){
        LaravelLogWatcher watcher = watchers.get(siteFile);
        LaravelSessionLogListener listener = new LaravelSessionLogListener(session);

        if(watcher == null){
            try{
                watcher = new LaravelLogWatcher(siteFile);
                watcher.addListener(listener);
                watcher.startWatching();
            }catch (IOException e){
                log.error("Failed to create new watcher for {}: ", siteFile, e);
            }
            return;
        }
        LaravelLogWatcher finalWatcher = watcher;
        watchers.forEachValue(5L, (v)->{
            if (v != finalWatcher){
                v.removeListener(session);
            }
        });
        watcher.addListener(listener);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
    }

    @Data
    static class  LaravelMessage{
        private String type;
        private Map<String, String> data;
    }
}
