package com.balancloud.device.protocol;

import com.balancloud.device.service.DeviceRouting;
import com.balancloud.device.service.DeviceTenantResolver;
import com.balancloud.mqtt.MqttDedupService;
import com.balancloud.mqtt.MqttLogService;
import com.balancloud.mqtt.event.UplinkMessageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UplinkFrameRouterTest {

    private static final String TOPIC = "balancloud/up/SIM-01/metric";
    private static final String PAYLOAD = "{\"deviceId\":\"SIM-01\",\"ports\":[]}";

    private ProtocolAdapterRegistry registry;
    private DeviceTenantResolver tenantResolver;
    private MqttLogService logService;
    private MqttDedupService dedupService;
    private ApplicationEventPublisher eventPublisher;
    private DeviceProtocolAdapter adapter;
    private UplinkFrameRouter router;

    @BeforeEach
    void setUp() {
        registry = mock(ProtocolAdapterRegistry.class);
        tenantResolver = mock(DeviceTenantResolver.class);
        logService = mock(MqttLogService.class);
        dedupService = mock(MqttDedupService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        adapter = mock(DeviceProtocolAdapter.class);
        router = new UplinkFrameRouter(registry, tenantResolver, logService, dedupService, eventPublisher);
    }

    private RawFrame frame() {
        return new RawFrame("mqtt", TOPIC, PAYLOAD.getBytes(StandardCharsets.UTF_8), java.util.Map.of(), Instant.now());
    }

    @Test
    void attributableFrameIsDecodedAndPublishedWithItsTenant() {
        PlatformEvent.Connectivity online = new PlatformEvent.Connectivity("SIM-01", true, 1L);
        when(tenantResolver.route("SIM-01")).thenReturn(new DeviceRouting(2L, "BALAN_MQTT", "SIM-01"));
        when(registry.require("BALAN_MQTT")).thenReturn(adapter);
        when(dedupService.firstSeen(2L, "SIM-01", "BALAN_MQTT", "metric", PAYLOAD)).thenReturn(true);
        when(adapter.decode(any())).thenReturn(List.of(online));

        router.route("SIM-01", "metric", frame());

        verify(logService).record("IN", TOPIC, "SIM-01", PAYLOAD);
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue()).isEqualTo(new UplinkEvent(2L, online));
    }

    @Test
    void unattributableFrameIsDroppedBeforeDecoding() {
        when(tenantResolver.route("SIM-01")).thenReturn(null);

        router.route("SIM-01", "metric", frame());

        verify(dedupService).firstSeen(isNull(), eq("SIM-01"), isNull(), eq("metric"), eq(PAYLOAD));
        verify(registry, never()).require(anyString());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void duplicateFrameIsDroppedBeforeDecoding() {
        when(tenantResolver.route("SIM-01")).thenReturn(new DeviceRouting(2L, "BALAN_MQTT", "SIM-01"));
        when(dedupService.firstSeen(2L, "SIM-01", "BALAN_MQTT", "metric", PAYLOAD)).thenReturn(false);

        router.route("SIM-01", "metric", frame());

        verify(registry, never()).require(anyString());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void unregisteredProtocolIsDroppedWithTheRegistryReason() {
        when(tenantResolver.route("SIM-01")).thenReturn(new DeviceRouting(2L, "CLOUDCHARGE", "SIM-01"));
        when(dedupService.firstSeen(2L, "SIM-01", "BALAN_MQTT", "metric", PAYLOAD)).thenReturn(true);
        when(registry.require("CLOUDCHARGE")).thenThrow(new IllegalStateException("协议未注册: CLOUDCHARGE"));

        assertThatCode(() -> router.route("SIM-01", "metric", frame())).doesNotThrowAnyException();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    /** 平台侧 OTA 进度还在听原始帧；解不出语义的帧必须原样重投，不能当成「没有这条报文」。 */
    @Test
    void unrecognizedFrameIsRepublishedRaw() {
        when(tenantResolver.route("SIM-01")).thenReturn(new DeviceRouting(2L, "BALAN_MQTT", "SIM-01"));
        when(dedupService.firstSeen(2L, "SIM-01", "BALAN_MQTT", "ota_status", PAYLOAD)).thenReturn(true);
        when(registry.require("BALAN_MQTT")).thenReturn(adapter);
        when(adapter.decode(any())).thenReturn(List.of(new PlatformEvent.Unrecognized("SIM-01", "未知事件 ota_status")));

        router.route("SIM-01", "ota_status", frame());

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(published.capture());
        UplinkMessageEvent message = (UplinkMessageEvent) published.getValue();
        assertThat(message.getDeviceNo()).isEqualTo("SIM-01");
        assertThat(message.getEvent()).isEqualTo("ota_status");
        assertThat(message.getPayload()).isEqualTo(PAYLOAD);
        assertThat(message.getTenantId()).isEqualTo(2L);
    }

    /** 回调线程里抛出去会打断整个 MQTT 订阅，解码异常只能吃掉这一帧。 */
    @Test
    void decodeFailureDoesNotBreakTheCallbackThread() {
        when(tenantResolver.route("SIM-01")).thenReturn(new DeviceRouting(2L, "BALAN_MQTT", "SIM-01"));
        when(dedupService.firstSeen(2L, "SIM-01", "BALAN_MQTT", "metric", PAYLOAD)).thenReturn(true);
        when(registry.require("BALAN_MQTT")).thenReturn(adapter);
        when(adapter.decode(any())).thenThrow(new IllegalStateException("bad frame"));

        assertThatCode(() -> router.route("SIM-01", "metric", frame())).doesNotThrowAnyException();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
