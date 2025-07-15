package com.x64dev.watcher.socks.laravel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@EnableWebSocket
@Configuration
public class LaravelSockConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(myHandler(),"/api/lara-sock" ).addInterceptors(new LaravelSockInterceptor());

    }

    @Bean
    LaravelSockHandler myHandler(){
        return  new LaravelSockHandler();
    }
}
