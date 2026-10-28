package com.balancloud.device.ocpp;

import com.balancloud.device.ocpp.config.OcppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.List;

/**
 * 只协商 {@code ocpp1.6}：桩没有请求这个子协议就不建立会话。
 * 用「协商后什么都不选」的默认行为会让一个普通 WebSocket 客户端也能连进来。
 */
@Component
public class OcppHandshakeHandler extends DefaultHandshakeHandler {

    private final OcppProperties properties;

    public OcppHandshakeHandler(OcppProperties properties) {
        this.properties = properties;
        setSupportedProtocols(properties.getSubprotocol());
    }

    @Override
    protected String selectProtocol(List<String> requestedProtocols, WebSocketHandler webSocketHandler) {
        if (requestedProtocols != null && requestedProtocols.contains(properties.getSubprotocol())) {
            return properties.getSubprotocol();
        }
        return null;
    }
}
