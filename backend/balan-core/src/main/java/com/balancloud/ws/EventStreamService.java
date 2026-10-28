package com.balancloud.ws;

import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.framework.cluster.ClusterProperties;
import com.balancloud.framework.security.LoginUser;
import com.balancloud.framework.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件流推送：看板与告警页面通过 /ws/events 接收实时事件，轮询作为兜底。
 * 推送按连接持有者的场站范围过滤，超级管理员不受限。
 * 多实例下事件经 Redis 频道转发给其他节点，本节点连接直投（不经回环）。
 */
@Service
public class EventStreamService {

    private static final Logger log = LoggerFactory.getLogger(EventStreamService.class);
    private static final String ADMIN_ROLE = "admin";
    private static final String ATTR_LOGIN_USER = "loginUser";
    private static final String ATTR_STATIONS = "stationIds";

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final ClusterProperties clusterProperties;
    private final ClusterNode clusterNode;

    public EventStreamService(ObjectMapper objectMapper,
                              StringRedisTemplate redisTemplate,
                              ClusterProperties clusterProperties,
                              ClusterNode clusterNode) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.clusterProperties = clusterProperties;
        this.clusterNode = clusterNode;
    }

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("Event stream client connected: {} (user={}, stations={})",
                session.getId(), username(session), stationScope(session));
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    public int onlineClients() {
        return sessions.size();
    }

    public void publish(String type, Long stationId, Object data) {
        if (sessions.isEmpty() && !clusterProperties.isWsBroadcast()) {
            return;
        }
        String message;
        try {
            message = objectMapper.writeValueAsString(new StreamEvent(type, System.currentTimeMillis(), data));
        } catch (Exception e) {
            log.warn("Failed to serialize event stream message type={}: {}", type, e.getMessage());
            return;
        }
        Long tenantId = TenantContext.get();
        deliverLocal(message, stationId, tenantId);
        broadcast(message, stationId, tenantId);
    }

    /**
     * 其他实例广播过来的事件：本实例发过的直接丢弃（本地已直投过），其余按本机会话再过滤一次。
     */
    public void onRemoteEvent(String body) {
        if (!StringUtils.hasText(body)) {
            return;
        }
        try {
            JsonNode envelope = objectMapper.readTree(body);
            if (clusterNode.isSelf(envelope.path("instanceId").asText(null))) {
                return;
            }
            String payload = envelope.path("payload").asText(null);
            if (!StringUtils.hasText(payload)) {
                return;
            }
            JsonNode stationNode = envelope.get("stationId");
            JsonNode tenantNode = envelope.get("tenantId");
            deliverLocal(payload,
                    stationNode == null || stationNode.isNull() ? null : stationNode.asLong(),
                    tenantNode == null || tenantNode.isNull()
                            ? TenantContext.DEFAULT_TENANT_ID : tenantNode.asLong());
        } catch (Exception e) {
            log.warn("Failed to deliver remote event stream message: {}", e.getMessage());
        }
    }

    /**
     * 事务内产生的事件在提交后再推送，避免前端收到事件却查不到对应数据。
     */
    public void publishAfterCommit(String type, Long stationId, Object data) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(type, stationId, data);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(type, stationId, data);
            }
        });
    }

    private void broadcast(String message, Long stationId, Long tenantId) {
        if (!clusterProperties.isWsBroadcast()) {
            return;
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("instanceId", clusterNode.id());
            envelope.put("tenantId", tenantId);
            envelope.put("stationId", stationId);
            envelope.put("payload", message);
            redisTemplate.convertAndSend(clusterProperties.getWsChannel(),
                    objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.warn("Failed to broadcast event stream message: {}", e.getMessage());
        }
    }

    private void deliverLocal(String message, Long stationId, Long tenantId) {
        if (sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions.values()) {
            if (!isOpen(session) || !allowedFor(session, stationId, tenantId)) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.debug("Failed to push event to {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    private boolean isOpen(WebSocketSession session) {
        return session != null && session.isOpen();
    }

    private boolean allowedFor(WebSocketSession session, Long stationId, Long eventTenantId) {
        Object user = session.getAttributes().get(ATTR_LOGIN_USER);
        if (!(user instanceof LoginUser loginUser) || !sameTenant(loginUser.getTenantId(), eventTenantId)) {
            return false;
        }
        if (stationId == null || loginUser.getRoles().contains(ADMIN_ROLE)) {
            return true;
        }
        Object stations = session.getAttributes().get(ATTR_STATIONS);
        if (stations instanceof Set<?> set) {
            return set.contains(stationId);
        }
        return false;
    }

    /**
     * 事件只投给同租户的会话：站级过滤之外还要挡住 null 站点的消息事件，
     * 否则跨实例转发会把「本租户内部广播」变成全平台广播。
     * 旧会话没有 tenantId，按默认租户处理。
     */
    private boolean sameTenant(Long sessionTenantId, Long eventTenantId) {
        Long tenantId = sessionTenantId == null ? TenantContext.DEFAULT_TENANT_ID : sessionTenantId;
        return tenantId.equals(eventTenantId);
    }

    private String username(WebSocketSession session) {
        Object user = session.getAttributes().get(ATTR_LOGIN_USER);
        return user instanceof LoginUser loginUser ? loginUser.getUsername() : "unknown";
    }

    private Object stationScope(WebSocketSession session) {
        return session.getAttributes().get(ATTR_STATIONS);
    }
}
