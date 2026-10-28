package com.balancloud.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class EventStreamHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EventStreamHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    private final EventStreamService eventStreamService;

    public EventStreamHandler(EventStreamService eventStreamService) {
        this.eventStreamService = eventStreamService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        eventStreamService.register(new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("ping".equals(message.getPayload().trim())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        eventStreamService.unregister(session);
        log.debug("Event stream client {} closed: {}", session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Event stream transport error on {}: {}", session.getId(), exception.getMessage());
    }
}
