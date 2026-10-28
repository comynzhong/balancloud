package com.balancloud.device.ocpp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 一条 OCPP 连接的生命周期：校验子协议 → 注册会话 → 把文本帧交给 {@link OcppFrameService}。
 *
 * <p>连接关闭时只在「注册表里移除的确实是这条会话」的情况下发离线事件：
 * 快速重连时旧连接的关闭回调不能把新连接标记成离线。
 */
@Component
public class OcppWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(OcppWebSocketHandler.class);

    private final OcppSessionRegistry registry;
    private final OcppFrameService frameService;

    public OcppWebSocketHandler(OcppSessionRegistry registry, OcppFrameService frameService) {
        this.registry = registry;
        this.frameService = frameService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session instanceof StandardWebSocketSession standard
                && !OcppProtocol.SUBPROTOCOL.equals(standard.getAcceptedProtocol())) {
            log.warn("Closing OCPP session {}: missing {} subprotocol", identityOf(session),
                    OcppProtocol.SUBPROTOCOL);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("ocpp1.6 required"));
            return;
        }
        String identity = identityOf(session);
        registry.register(identity, session);
        log.info("OCPP charge point {} connected ({} sessions)", identity, registry.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String identity = identityOf(session);
        Long tenantId = tenantIdOf(session);
        frameService.onInbound(identity, tenantId, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String identity = identityOf(session);
        if (registry.remove(identity, session)) {
            log.info("OCPP charge point {} disconnected: {}", identity, status);
            frameService.onDisconnected(identity, tenantIdOf(session));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("OCPP transport error on {}: {}", identityOf(session), exception.getMessage());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {
            // 连接已不可用，关闭失败无需处理。
        }
    }

    private String identityOf(WebSocketSession session) {
        Object identity = session.getAttributes().get(OcppHandshakeInterceptor.ATTR_IDENTITY);
        return identity == null ? "" : identity.toString();
    }

    private Long tenantIdOf(WebSocketSession session) {
        Object tenantId = session.getAttributes().get(OcppHandshakeInterceptor.ATTR_TENANT_ID);
        return tenantId instanceof Long value ? value : null;
    }
}
