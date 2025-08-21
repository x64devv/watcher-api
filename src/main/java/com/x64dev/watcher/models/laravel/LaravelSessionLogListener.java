package com.x64dev.watcher.models.laravel;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;

@Slf4j
public class LaravelSessionLogListener  implements LaravelLogListener{
    ObjectMapper mapper = new ObjectMapper();
    @Getter
    private WebSocketSession session;

    public LaravelSessionLogListener(WebSocketSession session){
        this.session = session;
    }
    @Override
    public void onLogEntry(LaravelLogEntry entry) {
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_OPTIONALS);
        try{
            HashMap<String,Object> liveLogMsg = new HashMap<>();
            liveLogMsg.put("type", "live_log");
            liveLogMsg.put("log", entry);
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
