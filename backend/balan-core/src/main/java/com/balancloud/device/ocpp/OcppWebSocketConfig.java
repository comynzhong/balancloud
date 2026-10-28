package com.balancloud.device.ocpp;

import com.balancloud.device.ocpp.config.OcppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * OCPP Central System 的 WebSocket 端点：{@code /ocpp/{identity}}。
 *
 * <p>与后台事件流 {@code /ws/events} 分开注册：两者的握手鉴权、子协议、允许来源都不同。
 */
@Configuration
@EnableWebSocket
public class OcppWebSocketConfig implements WebSocketConfigurer {

    private final OcppWebSocketHandler handler;
    private final OcppHandshakeInterceptor interceptor;
    private final OcppHandshakeHandler handshakeHandler;
    private final OcppProperties properties;

    public OcppWebSocketConfig(OcppWebSocketHandler handler,
                               OcppHandshakeInterceptor interceptor,
                               OcppHandshakeHandler handshakeHandler,
                               OcppProperties properties) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.handshakeHandler = handshakeHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (!properties.isEnabled()) {
            return;
        }
        registry.addHandler(handler, "/ocpp/**")
                .addInterceptors(interceptor)
                .setHandshakeHandler(handshakeHandler)
                // CP 通常不发 Origin；这里不能沿用后台事件流的 localhost 白名单。
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean ocppWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.getMaxFrameBytes());
        container.setMaxBinaryMessageBufferSize(properties.getMaxFrameBytes());
        return container;
    }
}
