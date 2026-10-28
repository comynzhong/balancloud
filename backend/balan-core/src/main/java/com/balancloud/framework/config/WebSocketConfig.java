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
    private final WebOriginProperties webOriginProperties;

    public WebSocketConfig(EventStreamHandler eventStreamHandler,
                           EventStreamAuthInterceptor eventStreamAuthInterceptor,
                           WebOriginProperties webOriginProperties) {
        this.eventStreamHandler = eventStreamHandler;
        this.eventStreamAuthInterceptor = eventStreamAuthInterceptor;
        this.webOriginProperties = webOriginProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(eventStreamHandler, "/ws/events")
                .addInterceptors(eventStreamAuthInterceptor)
                .setAllowedOriginPatterns(webOriginProperties.getAllowedOriginPatterns().toArray(new String[0]));
    }
}
