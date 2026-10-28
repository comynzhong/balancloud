package com.balancloud.device.ocpp;

import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.framework.cluster.ClusterProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下行投递的两条路：本地有会话直发；没有就广播。Redis 不可用时必须明确失败，
 * 不能「记成功」让指令永远停在 SENT。
 */
class OcppDownlinkBusTest {

    private static final String IDENTITY = "CP-DC-001";
    private static final String FRAME = "[2,\"u-1\",\"RemoteStartTransaction\",{}]";

    private final OcppSessionRegistry registry = new OcppSessionRegistry();
    private final OcppPendingStore pendingStore = mock(OcppPendingStore.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClusterProperties properties = new ClusterProperties();
    private final ClusterNode node;
    private final OcppDownlinkBus bus;

    private final WebSocketSession session = mock(WebSocketSession.class);

    OcppDownlinkBusTest() {
        properties.setInstanceId("node-a");
        node = new ClusterNode(properties);
        bus = new OcppDownlinkBus(registry, pendingStore, redisTemplate, properties, node, objectMapper);
    }

    @BeforeEach
    void registerPendingLookup() {
        when(session.isOpen()).thenReturn(true);
    }

    @Test
    void localSessionSendsDirectlyWithoutRedis() {
        registry.register(IDENTITY, session);

        assertThat(bus.send(frame())).isTrue();

        assertThat(sentText()).isEqualTo(FRAME);
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void registersThePendingCommandBeforeSending() {
        registry.register(IDENTITY, session);

        bus.send(frame());

        verify(pendingStore).register(eq(IDENTITY), eq("u-1"),
                eq(new OcppPendingStore.PendingCommand("cmd-1", "START", 2)));
    }

    @Test
    void withoutALocalSessionTheFrameIsBroadcastToOtherInstances() {
        assertThat(bus.send(frame())).isTrue();

        ArgumentCaptor<String> envelope = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("balan:ocpp:down"), envelope.capture());
        assertThat(envelope.getValue())
                .contains("\"instanceId\":\"node-a\"")
                .contains("\"identity\":\"" + IDENTITY + "\"")
                .contains("RemoteStartTransaction");
    }

    @Test
    void redisFailureIsReportedAsFailureNotFakeSuccess() {
        when(redisTemplate.convertAndSend(anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThat(bus.send(frame())).isFalse();
    }

    @Test
    void remoteEnvelopeIsOnlyDeliveredByTheInstanceHoldingTheSession() throws Exception {
        registry.register(IDENTITY, session);

        bus.onRemoteMessage(objectMapper.writeValueAsString(
                new OcppDownlinkBus.Envelope("node-b", IDENTITY, FRAME)));
        assertThat(sentText()).isEqualTo(FRAME);

        // 自己发出的广播不回环
        bus.onRemoteMessage(objectMapper.writeValueAsString(
                new OcppDownlinkBus.Envelope("node-a", IDENTITY, FRAME)));
        verify(session, org.mockito.Mockito.times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void malformedRemoteEnvelopeIsDroppedWithoutCrashing() throws Exception {
        bus.onRemoteMessage("not-json");
        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    private com.balancloud.device.protocol.RawFrame frame() {
        return new com.balancloud.device.protocol.RawFrame("ocpp", IDENTITY,
                FRAME.getBytes(StandardCharsets.UTF_8),
                Map.of("ocpp.uniqueId", "u-1", "ocpp.cmdId", "cmd-1",
                        "ocpp.type", "START", "ocpp.portNo", "2"),
                Instant.now());
    }

    private String sentText() {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        try {
            verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        } catch (java.io.IOException e) {
            throw new AssertionError("发送失败", e);
        }
        return captor.getValue().getPayload();
    }
}
