package com.balancloud.device.ocpp;

import com.balancloud.device.ocpp.config.OcppProperties;
import com.balancloud.device.ocpp.domain.OcppTransaction;
import com.balancloud.device.ocpp.service.OcppTransactionStore;
import com.balancloud.device.protocol.PlatformEvent;
import com.balancloud.device.protocol.RawFrame;
import com.balancloud.device.protocol.UplinkEvent;
import com.balancloud.device.protocol.UplinkFrameRouter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 帧信封层：CALL 先走上行路由再回执，CALLRESULT/CALLERROR 回到待回执映射。
 * 这里不重复适配器的业务语义，只钉住「回什么、回不回、什么时候回」。
 */
class OcppFrameServiceTest {

    private static final String IDENTITY = "CP-DC-001";
    private static final long TENANT = 1L;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final OcppSessionRegistry registry = new OcppSessionRegistry();
    private final OcppProperties properties = new OcppProperties();
    private final UplinkFrameRouter uplinkRouter = mock(UplinkFrameRouter.class);
    private final OcppPendingStore pendingStore = mock(OcppPendingStore.class);
    private final OcppTransactionStore transactionStore = mock(OcppTransactionStore.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final WebSocketSession session = mock(WebSocketSession.class);

    private final OcppFrameService service = new OcppFrameService(objectMapper, registry, properties,
            uplinkRouter, pendingStore, transactionStore, eventPublisher);

    @BeforeEach
    void connectSession() {
        when(session.isOpen()).thenReturn(true);
        registry.register(IDENTITY, session);
    }

    @Test
    void unsupportedActionGetsAnEmptyResultInsteadOfACallError() {
        service.onInbound(IDENTITY, TENANT, "[2,\"u-9\",\"DataTransfer\",{}]");

        // 回 CALLERROR 会让桩按错误策略反复重试；空 CALLRESULT 才是「收到但不处理」（§48.2）
        assertThat(lastFrame().path(0).asInt()).isEqualTo(3);
        assertThat(lastFrame().path(1).asText()).isEqualTo("u-9");
        assertThat(lastFrame().path(2).isObject()).isTrue();
        assertThat(lastFrame().path(2).size()).isZero();
        verify(uplinkRouter, never()).route(anyString(), anyString(), any());
    }

    @Test
    void supportedCallIsRoutedBeforeTheConfirmationGoesOut() {
        service.onInbound(IDENTITY, TENANT, "[2,\"u-1\",\"BootNotification\",{\"chargePointVendor\":\"Balan\"}]");

        ArgumentCaptor<RawFrame> frame = ArgumentCaptor.forClass(RawFrame.class);
        verify(uplinkRouter).route(eq(IDENTITY), eq("BootNotification"), frame.capture());
        assertThat(frame.getValue().channel()).isEqualTo("ocpp");
        assertThat(frame.getValue().headers()).containsEntry("ocpp.uniqueId", "u-1");

        JsonNode result = lastFrame().path(2);
        assertThat(result.path("status").asText()).isEqualTo("Accepted");
        assertThat(result.path("interval").asInt()).isEqualTo(300);
    }

    @Test
    void businessFailureStillAnswersSoTheChargePointStopsRetrying() {
        doThrow(new IllegalStateException("db down"))
                .when(uplinkRouter).route(anyString(), anyString(), any());

        service.onInbound(IDENTITY, TENANT, "[2,\"u-2\",\"Heartbeat\",{}]");

        assertThat(lastFrame().path(0).asInt()).isEqualTo(3);
        assertThat(lastFrame().path(2).path("currentTime").asText()).isNotBlank();
    }

    @Test
    void startTransactionWithoutAPlatformOrderIsRejectedWithTransactionIdZero() {
        when(transactionStore.findActiveAnyTenant(IDENTITY, 1)).thenReturn(null);

        service.onInbound(IDENTITY, TENANT,
                "[2,\"u-3\",\"StartTransaction\",{\"connectorId\":1,\"idTag\":\"BALAN\",\"meterStart\":0}]");

        JsonNode result = lastFrame().path(2);
        assertThat(result.path("transactionId").asInt()).isZero();
        assertThat(result.path("idTagInfo").path("status").asText()).isEqualTo("Invalid");
    }

    @Test
    void startTransactionEchoesThePlatformTransactionId() {
        OcppTransaction tx = new OcppTransaction();
        tx.setId(42L);
        when(transactionStore.findActiveAnyTenant(IDENTITY, 1)).thenReturn(tx);

        service.onInbound(IDENTITY, TENANT,
                "[2,\"u-4\",\"StartTransaction\",{\"connectorId\":1,\"idTag\":\"BALAN\",\"meterStart\":0}]");

        JsonNode result = lastFrame().path(2);
        assertThat(result.path("transactionId").asInt()).isEqualTo(42);
        assertThat(result.path("idTagInfo").path("status").asText()).isEqualTo("Accepted");
    }

    @Test
    void acceptedCallResultPublishesASuccessfulCommandAck() {
        when(pendingStore.take(IDENTITY, "u-5"))
                .thenReturn(new OcppPendingStore.PendingCommand("cmd-1", "START", 2));

        service.onInbound(IDENTITY, TENANT, "[3,\"u-5\",{\"status\":\"Accepted\"}]");

        UplinkEvent event = capturedEvent();
        assertThat(event.tenantId()).isEqualTo(TENANT);
        PlatformEvent.CommandAck ack = (PlatformEvent.CommandAck) event.event();
        assertThat(ack.cmdId()).isEqualTo("cmd-1");
        assertThat(ack.portNo()).isEqualTo(2);
        assertThat(ack.type()).isEqualTo("START");
        assertThat(ack.success()).isTrue();
        assertThat(ack.rawResult()).isEqualTo("Accepted");
    }

    @Test
    void rejectedCallResultAndCallErrorBothBecomeFailedAcks() {
        when(pendingStore.take(IDENTITY, "u-6"))
                .thenReturn(new OcppPendingStore.PendingCommand("cmd-2", "STOP", 1));
        service.onInbound(IDENTITY, TENANT, "[3,\"u-6\",{\"status\":\"Rejected\"}]");
        assertThat(((PlatformEvent.CommandAck) capturedEvent().event()).success()).isFalse();

        when(pendingStore.take(IDENTITY, "u-7"))
                .thenReturn(new OcppPendingStore.PendingCommand("cmd-3", "UNLOCK", 1));
        service.onInbound(IDENTITY, TENANT,
                "[4,\"u-7\",\"NotSupported\",\"unlock not supported\",{}]");
        PlatformEvent.CommandAck ack = (PlatformEvent.CommandAck) capturedEvent().event();
        assertThat(ack.success()).isFalse();
        assertThat(ack.rawResult()).isEqualTo("NotSupported");
        assertThat(ack.message()).isEqualTo("unlock not supported");
    }

    @Test
    void unmatchedAckIsIgnoredQuietly() {
        when(pendingStore.take(anyString(), anyString())).thenReturn(null);

        service.onInbound(IDENTITY, TENANT, "[3,\"u-8\",{\"status\":\"Accepted\"}]");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void malformedOrOversizedFramesAreDroppedBeforeRouting() throws Exception {
        service.onInbound(IDENTITY, TENANT, "{not json");
        service.onInbound(IDENTITY, TENANT, "{\"type\":2}");
        service.onInbound(IDENTITY, TENANT, "[]");
        service.onInbound(IDENTITY, TENANT, "");

        properties.setMaxFrameBytes(8);
        service.onInbound(IDENTITY, TENANT, "[2,\"too-long\",\"Heartbeat\",{}]");

        verify(uplinkRouter, never()).route(anyString(), anyString(), any());
        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    private JsonNode lastFrame() {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        try {
            verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        } catch (java.io.IOException e) {
            throw new AssertionError("回执发送失败", e);
        }
        try {
            return objectMapper.readTree(captor.getValue().getPayload());
        } catch (Exception e) {
            throw new AssertionError("回执不是合法 JSON: " + captor.getValue().getPayload(), e);
        }
    }

    private UplinkEvent capturedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        return (UplinkEvent) captor.getAllValues().get(captor.getAllValues().size() - 1);
    }
}
