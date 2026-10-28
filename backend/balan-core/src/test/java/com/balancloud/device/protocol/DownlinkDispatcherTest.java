package com.balancloud.device.protocol;

import com.balancloud.device.ocpp.OcppDownlinkBus;
import com.balancloud.mqtt.MqttGateway;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下行必须按 channel 选传输：把 OCPP 帧塞进 MQTT 网关等于「看起来发出去了，实际到不了桩」。
 */
class DownlinkDispatcherTest {

    private final MqttGateway mqttGateway = mock(MqttGateway.class);
    private final OcppDownlinkBus ocppDownlinkBus = mock(OcppDownlinkBus.class);
    private final DownlinkDispatcher dispatcher = new DownlinkDispatcher(mqttGateway, ocppDownlinkBus);

    @Test
    void mqttFramesGoToTheMqttGatewayOnly() {
        RawFrame frame = RawFrame.mqtt("balancloud/down/BC-S12-001/cmd", "{\"cmdId\":\"c1\"}");
        when(mqttGateway.publishRaw(frame)).thenReturn(true);

        assertThat(dispatcher.send(frame)).isTrue();
        verify(mqttGateway).publishRaw(frame);
        verify(ocppDownlinkBus, never()).send(any());
    }

    @Test
    void ocppFramesGoToTheWebSocketBusOnly() {
        RawFrame frame = new RawFrame("ocpp", "CP-DC-001",
                "[2,\"u-1\",\"RemoteStartTransaction\",{}]".getBytes(StandardCharsets.UTF_8),
                Map.of("ocpp.uniqueId", "u-1"), Instant.now());
        when(ocppDownlinkBus.send(frame)).thenReturn(true);

        assertThat(dispatcher.send(frame)).isTrue();
        verify(ocppDownlinkBus).send(frame);
        verify(mqttGateway, never()).publishRaw(any());
    }

    @Test
    void emptyFramesAreRejectedBeforeHittingEitherTransport() {
        RawFrame empty = new RawFrame("ocpp", "CP-DC-001", new byte[0], Map.of(), Instant.now());
        assertThat(dispatcher.send(empty)).isFalse();

        RawFrame noPeer = new RawFrame("mqtt", "", "{}".getBytes(StandardCharsets.UTF_8),
                Map.of(), Instant.now());
        assertThat(dispatcher.send(noPeer)).isFalse();
        assertThat(dispatcher.send(null)).isFalse();

        verify(mqttGateway, never()).publishRaw(any());
        verify(ocppDownlinkBus, never()).send(any());
    }

    @Test
    void unknownChannelFailsInsteadOfGuessing() {
        RawFrame frame = new RawFrame("coap", "CP-DC-001", "{}".getBytes(StandardCharsets.UTF_8),
                Map.of(), Instant.now());
        assertThat(dispatcher.send(frame)).isFalse();
        verify(mqttGateway, never()).publishRaw(any());
        verify(ocppDownlinkBus, never()).send(any());
    }
}
