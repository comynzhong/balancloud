package com.balancloud.ws;

import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.framework.cluster.ClusterProperties;
import com.balancloud.framework.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventStreamServiceTest {

    private StringRedisTemplate redisTemplate;
    private ClusterProperties clusterProperties;
    private ClusterNode nodeA;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();
        clusterProperties = new ClusterProperties();
        clusterProperties.setInstanceId("node-a");
        nodeA = new ClusterNode(clusterProperties);
    }

    private EventStreamService service(ClusterNode node) {
        return new EventStreamService(objectMapper, redisTemplate, clusterProperties, node);
    }

    private WebSocketSession session(String username, Long tenantId, boolean admin, Long... stationIds) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUsername(username);
        loginUser.setTenantId(tenantId);
        loginUser.setRoles(new LinkedHashSet<>(admin ? java.util.List.of("admin") : java.util.List.of("viewer")));
        loginUser.setStationIds(new LinkedHashSet<>(java.util.Arrays.asList(stationIds)));
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("loginUser", loginUser);
        attributes.put("stationIds", loginUser.getStationIds());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(username + "-" + tenantId);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    @Test
    void localPushIsDeliveredDirectlyAndForwardedToOtherInstances() throws Exception {
        EventStreamService service = service(nodeA);
        WebSocketSession session = session("operator", 1L, false, 7L);
        service.register(session);

        service.publish("alert", 7L, Map.of("id", 12));

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(sent.capture());
        assertThat(sent.getValue().getPayload()).contains("\"type\":\"alert\"");

        ArgumentCaptor<String> envelope = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("balancloud:ws:event"), envelope.capture());
        assertThat(envelope.getValue()).contains("node-a").contains("\"stationId\":7").contains("\"tenantId\":1");
    }

    @Test
    void remoteEventReachesSessionsThatThisInstanceWouldHaveMissed() throws Exception {
        EventStreamService service = service(nodeA);
        WebSocketSession session = session("operator", 1L, false, 7L);
        service.register(session);

        service.onRemoteEvent("{\"instanceId\":\"node-b\",\"tenantId\":1,\"stationId\":7,"
                + "\"payload\":\"{\\\"type\\\":\\\"device\\\",\\\"ts\\\":1,\\\"data\\\":{}}\"}");

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void ownBroadcastIsNotDeliveredTwice() throws Exception {
        EventStreamService service = service(nodeA);
        WebSocketSession session = session("operator", 1L, false, 7L);
        service.register(session);

        service.onRemoteEvent("{\"instanceId\":\"node-a\",\"tenantId\":1,\"stationId\":7,"
                + "\"payload\":\"{\\\"type\\\":\\\"device\\\"}\"}");

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void remoteEventKeepsStationAndTenantFilters() throws Exception {
        EventStreamService service = service(nodeA);
        WebSocketSession otherStation = session("operator", 1L, false, 8L);
        WebSocketSession otherTenant = session("tenant2admin", 2L, true, 7L);
        service.register(otherStation);
        service.register(otherTenant);

        service.onRemoteEvent("{\"instanceId\":\"node-b\",\"tenantId\":1,\"stationId\":7,"
                + "\"payload\":\"{\\\"type\\\":\\\"alert\\\"}\"}");

        verify(otherStation, never()).sendMessage(any(TextMessage.class));
        verify(otherTenant, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastCanBeSwitchedOff() {
        clusterProperties.setWsBroadcast(false);
        EventStreamService service = service(nodeA);

        service.publish("alert", 7L, Map.of("id", 12));

        verify(redisTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
