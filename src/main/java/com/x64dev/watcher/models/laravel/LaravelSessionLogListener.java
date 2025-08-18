package com.x64dev.watcher.models.laravel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;

@Slf4j
public class LaravelSessionLogListener  implements LaravelLogListener{

    @Autowired
    ObjectMapper mapper;
    @Getter
    private WebSocketSession session;

    public LaravelSessionLogListener(WebSocketSession session){
        this.session = session;
    }
    @Override
    public void onLogEntry(LaravelLogEntry entry) {
        try{
            HashMap<String,String> liveLogMsg = new HashMap<>();
            liveLogMsg.put("type", "live_log");
            liveLogMsg.put("log", mapper.writeValueAsString(entry));
            session.sendMessage(new TextMessage(mapper.writeValueAsString(liveLogMsg)));
        }catch (IOException e){
           log.error("Failed to send live log: ", e);
        }
    }

    @Override
    public void onError(Exception error) {
        log.error("=== Error thrown from the Listener === ", error);
    }
}
