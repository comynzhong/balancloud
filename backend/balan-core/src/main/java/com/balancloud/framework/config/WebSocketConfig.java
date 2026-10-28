package com.balancloud.framework.config;

import com.balancloud.ws.EventStreamAuthInterceptor;
import com.balancloud.ws.EventStreamHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EventStreamHandler eventStreamHandler;
    private final EventStreamAuthInterceptor eventStreamAuthInterceptor;

    public WebSocketConfig(EventStreamHandler eventStreamHandler,
                           EventStreamAuthInterceptor eventStreamAuthInterceptor) {
        this.eventStreamHandler = eventStreamHandler;
        this.eventStreamAuthInterceptor = eventStreamAuthInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(eventStreamHandler, "/ws/events")
                .addInterceptors(eventStreamAuthInterceptor)
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
