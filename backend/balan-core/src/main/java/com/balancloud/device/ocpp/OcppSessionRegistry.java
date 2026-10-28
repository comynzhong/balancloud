package com.balancloud.device.ocpp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CP identity → WebSocket 会话。一个 identity 只允许一条连接：
 * 同一台桩重连时关掉旧连接，避免指令从两条会话里各发一遍。
 */
@Component
public class OcppSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(OcppSessionRegistry.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String identity, WebSocketSession session) {
        WebSocketSession previous = sessions.put(identity, session);
        if (previous != null && previous != session) {
            closeQuietly(previous);
            log.info("Replaced previous OCPP session for {}", identity);
        }
    }

    /** 只有当前注册的还是这条会话时才移除；重连后的新会话不会被旧连接的关闭回调误删。 */
    public boolean remove(String identity, WebSocketSession session) {
        return sessions.remove(identity, session);
    }

    public boolean hasSession(String identity) {
        WebSocketSession session = sessions.get(identity);
        return session != null && session.isOpen();
    }

    public boolean send(String identity, String text) {
        WebSocketSession session = sessions.get(identity);
        if (session == null || !session.isOpen()) {
            return false;
        }
        // WebSocketSession 不是线程安全的：回执线程与下行线程会同时写同一条连接。
        synchronized (session) {
            try {
                session.sendMessage(new TextMessage(text));
                return true;
            } catch (Exception e) {
                log.warn("Failed to send OCPP frame to {}: {}", identity, e.getMessage());
                sessions.remove(identity, session);
                closeQuietly(session);
                return false;
            }
        }
    }

    public int size() {
        return sessions.size();
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (Exception ignored) {
            // 连接已经断了，关闭失败没有补救动作。
        }
    }
}
