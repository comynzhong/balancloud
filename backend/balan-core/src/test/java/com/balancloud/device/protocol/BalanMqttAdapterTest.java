package com.balancloud.device.protocol;

import com.balancloud.command.model.CommandType;
import com.balancloud.mqtt.model.MetricPayload;
import com.balancloud.mqtt.model.PortMetric;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BALAN_MQTT 抽取前后的等价性（§46 Step 2）：同一份报文，解出来的字段必须和原来直接绑定
 * {@code MetricPayload} 完全一致。这里断言的是「搬运没搬坏」，不是新语义。
 */
class BalanMqttAdapterTest {

    private static final String DEVICE = "BC20260001";

    private final ObjectMapper objectMapper = prodLikeMapper();
    private final BalanMqttAdapter adapter = new BalanMqttAdapter(objectMapper);

    /** 对齐 Spring Boot 的默认配置：未知字段不报错，否则测试会比生产更严。 */
    private static ObjectMapper prodLikeMapper() {
        return JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    private RawFrame uplink(String event, String payload) {
        return RawFrame.mqtt("balancloud/up/" + DEVICE + "/" + event, payload);
    }

    @Test
    void metricFrameDecodesToTheSamePortsAsTheOldBinding() throws Exception {
        String payload = """
                {"deviceId":"BC20260001","ts":1762000000000,"ports":[
                  {"portNo":1,"status":"CHARGING","voltage":220.5,"currentA":8.2,"temperature":41.3,
                   "powerKw":1.8,"energyTotal":24.9146,"signalStrength":-63,"plugged":true},
                  {"portNo":2,"status":null,"voltage":221.0,"currentA":0.0,"temperature":39.8,
                   "powerKw":0.0,"energyTotal":11.5,"signalStrength":-70,"plugged":false}]}
                """;
        // 改造前的路径：Jackson 直接绑到 MetricPayload，业务代码读 PortMetric
        MetricPayload legacy = objectMapper.readValue(payload, MetricPayload.class);
        PlatformEvent.MetricReport decoded = (PlatformEvent.MetricReport) only(adapter.decode(uplink("metric", payload)));

        assertThat(decoded.deviceNo()).isEqualTo(DEVICE);
        assertThat(decoded.ts()).isEqualTo(1762000000000L);
        assertThat(decoded.ports()).hasSize(legacy.getPorts().size());
        for (int i = 0; i < legacy.getPorts().size(); i++) {
            PortMetric old = legacy.getPorts().get(i);
            PortSample now = decoded.ports().get(i);
            assertThat(now.portNo()).isEqualTo(old.getPortNo());
            assertThat(now.status()).isEqualTo(old.getStatus());
            assertThat(now.voltage()).isEqualTo(old.getVoltage());
            assertThat(now.currentA()).isEqualTo(old.getCurrentA());
            assertThat(now.temperature()).isEqualTo(old.getTemperature());
            assertThat(now.powerKw()).isEqualTo(old.getPowerKw());
            assertThat(now.energyTotal()).isEqualTo(old.getEnergyTotal());
            assertThat(now.signalStrength()).isEqualTo(old.getSignalStrength());
            assertThat(now.plugged()).isEqualTo(old.getPlugged());
            // 两轮插座没有的字段必须由适配器留空，不能造默认值
            assertThat(now.soc()).isNull();
            assertThat(now.stopReasonCode()).isNull();
            assertThat(now.meterSerial()).isNull();
        }
    }

    @Test
    void missingOrZeroTimestampFallsBackToNowExactlyLikeBefore() {
        String payload = """
                {"ports":[{"portNo":1,"currentA":0.5}]}
                """;
        long before = System.currentTimeMillis();
        PlatformEvent.MetricReport report =
                (PlatformEvent.MetricReport) only(adapter.decode(uplink("metric", payload)));
        long after = System.currentTimeMillis();

        assertThat(report.ts()).isBetween(before, after);
        assertThat(report.ports()).hasSize(1);
        // 设备没说 plugged 就是 null，占位费判定要能区分「没说」和「没插」
        assertThat(report.ports().get(0).plugged()).isNull();
    }

    @Test
    void emptyPortListProducesNoEvent() {
        assertThat(adapter.decode(uplink("metric", "{\"ts\":1,\"ports\":[]}"))).isEmpty();
        assertThat(adapter.decode(uplink("metric", "{\"ts\":1}"))).isEmpty();
    }

    @Test
    void malformedMetricPayloadIsDroppedNotThrown() {
        assertThat(adapter.decode(uplink("metric", "{oops"))).isEmpty();
    }

    @Test
    void onlineFrameWithoutOnlineFieldMeansOnline() {
        // 心跳帧只带 ts，原逻辑同样按上线处理
        PlatformEvent.Connectivity event =
                (PlatformEvent.Connectivity) only(adapter.decode(uplink("online", "{\"ts\":1762000000000}")));
        assertThat(event.online()).isTrue();
        assertThat(event.ts()).isEqualTo(1762000000000L);

        PlatformEvent.Connectivity explicitOffline = (PlatformEvent.Connectivity) only(
                adapter.decode(uplink("online", "{\"online\":false}")));
        assertThat(explicitOffline.online()).isFalse();

        PlatformEvent.Connectivity broken =
                (PlatformEvent.Connectivity) only(adapter.decode(uplink("online", "{oops")));
        assertThat(broken.online()).isTrue();
    }

    @Test
    void alertKeepsVendorTypeAndMarksUnmappedCodes() {
        PlatformEvent.VendorAlert known = (PlatformEvent.VendorAlert) only(
                adapter.decode(uplink("alert", "{\"portNo\":3,\"type\":\"OVER_TEMP\",\"level\":\"CRITICAL\","
                        + "\"message\":\"端口 3 过温\",\"value\":85.2}")));
        assertThat(known.hint().alertTypeName()).isEqualTo("OVER_TEMP");
        assertThat(known.hint().recognized()).isTrue();
        assertThat(known.message()).isEqualTo("端口 3 过温");
        assertThat(known.value()).isEqualTo(85.2);
        // 原逻辑：映射不上按端口故障处理，并把厂商类型写进正文
        PlatformEvent.VendorAlert unknown = (PlatformEvent.VendorAlert) only(
                adapter.decode(uplink("alert", "{\"portNo\":3,\"type\":\"cdz_e07\",\"message\":\"\",\"value\":null}")));
        assertThat(unknown.hint().alertTypeName()).isEqualTo("PORT_FAULT");
        assertThat(unknown.hint().recognized()).isFalse();
        assertThat(unknown.hint().vendorType()).isEqualTo("cdz_e07");
        assertThat(unknown.message()).isEqualTo("端口故障（厂商类型 cdz_e07）");
    }

    @Test
    void commandAckKeepsRawResultForThePlatformCopy() {
        PlatformEvent.CommandAck ack = (PlatformEvent.CommandAck) only(
                adapter.decode(uplink("cmd_ack", "{\"cmdId\":\"C-1\",\"portNo\":2,\"type\":\"START\","
                        + "\"result\":\"SUCCESS\",\"message\":\"已开始\",\"ts\":1762000000000}")));
        assertThat(ack.cmdId()).isEqualTo("C-1");
        assertThat(ack.portNo()).isEqualTo(2);
        assertThat(ack.type()).isEqualTo("START");
        assertThat(ack.success()).isTrue();
        assertThat(ack.rawResult()).isEqualTo("SUCCESS");
        assertThat(ack.message()).isEqualTo("已开始");

        PlatformEvent.CommandAck failed = (PlatformEvent.CommandAck) only(
                adapter.decode(uplink("cmd_ack", "{\"cmdId\":\"C-2\",\"result\":\"BUSY\"}")));
        assertThat(failed.success()).isFalse();
        // 原文不能只留一个 boolean：指令状态机的失败文案要拼「设备执行失败: BUSY」
        assertThat(failed.rawResult()).isEqualTo("BUSY");
        assertThat(failed.cmdId()).isEqualTo("C-2");
    }

    @Test
    void ackWithoutCmdIdStillBecomesAnEventBecauseTheStateMachineRejectsIt() {
        // cmdId 缺失/未知的判重与告警留在 CommandService，适配器不替它做决定
        PlatformEvent.CommandAck ack =
                (PlatformEvent.CommandAck) only(adapter.decode(uplink("cmd_ack", "{\"result\":\"SUCCESS\"}")));
        assertThat(ack.cmdId()).isNull();
    }

    @Test
    void orderEventKeepsTheRawStopReasonAndMeterReading() {
        PlatformEvent.OrderLifecycle event = (PlatformEvent.OrderLifecycle) only(
                adapter.decode(uplink("order_event", "{\"orderNo\":\"O20261026001\",\"portNo\":1,"
                        + "\"stopReason\":\"FULL\",\"energyTotal\":12.34565,\"ts\":1762000000000}")));
        assertThat(event.orderNo()).isEqualTo("O20261026001");
        assertThat(event.portNo()).isEqualTo(1);
        assertThat(event.stopReason()).isEqualTo("FULL");
        // 取整到 4 位是订单侧的事，不在这里做
        assertThat(event.energyTotal()).isEqualTo(12.34565);

        PlatformEvent.OrderLifecycle noOrderNo = (PlatformEvent.OrderLifecycle) only(
                adapter.decode(uplink("order_event", "{\"stopReason\":\"whatever\"}")));
        assertThat(noOrderNo.orderNo()).isNull();
        assertThat(noOrderNo.stopReason()).isEqualTo("whatever");
    }

    @Test
    void unknownEventBecomesUnrecognizedInsteadOfSilentDefault() {
        PlatformEvent event = only(adapter.decode(uplink("battery_swap", "{\"x\":1}")));
        assertThat(event).isInstanceOf(PlatformEvent.Unrecognized.class);
        assertThat(((PlatformEvent.Unrecognized) event).deviceNo()).isEqualTo(DEVICE);
    }

    @Test
    void topicOutsideTheUplinkShapeIsUnrecognized() {
        assertThat(adapter.decode(RawFrame.mqtt("balancloud/up/BC20260001", "{}")))
                .singleElement().isInstanceOf(PlatformEvent.Unrecognized.class);
        assertThat(adapter.decode(RawFrame.mqtt("other/topic/a/b", "{}")))
                .singleElement().isInstanceOf(PlatformEvent.Unrecognized.class);
    }

    @Test
    void encodeProducesTheSameDownlinkEnvelopeAsCommandServiceDid() {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("orderNo", "O20261026001");
        params.put("maxDurationMinutes", 480);
        PlatformCommand command = new PlatformCommand(PlatformCommand.ACTION_CMD, "C-9", DEVICE, 3,
                CommandType.START, params, 1762000000000L);

        RawFrame frame = adapter.encode(command);

        assertThat(frame.channel()).isEqualTo("mqtt");
        assertThat(frame.topicOrPeer()).isEqualTo("balancloud/down/" + DEVICE + "/cmd");
        // 字段顺序与 CommandService.deliver 完全一致：cmdId, deviceId, portNo, type, params, ts
        assertThat(frame.bodyAsText()).isEqualTo(
                "{\"cmdId\":\"C-9\",\"deviceId\":\"" + DEVICE + "\",\"portNo\":3,\"type\":\"START\","
                        + "\"params\":{\"orderNo\":\"O20261026001\",\"maxDurationMinutes\":480},\"ts\":1762000000000}");
    }

    @Test
    void encodeOtaUsesTheOtaTopicAndAFlatBody() {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("taskNo", "OT001");
        fields.put("version", "1.4.2");
        RawFrame frame = adapter.encode(PlatformCommand.ota(DEVICE, fields));

        assertThat(frame.topicOrPeer()).isEqualTo("balancloud/down/" + DEVICE + "/ota");
        assertThat(frame.bodyAsText()).isEqualTo("{\"taskNo\":\"OT001\",\"version\":\"1.4.2\"}");
    }

    @Test
    void capabilitiesCoverWhatTheBuiltinProtocolActuallyHandles() {
        ProtocolCapabilities capabilities = adapter.capabilities();

        assertThat(capabilities.remoteStart()).isTrue();
        assertThat(capabilities.remoteStop()).isTrue();
        assertThat(capabilities.unlock()).isTrue();
        assertThat(capabilities.setThreshold()).isTrue();
        assertThat(capabilities.ota()).isTrue();
        assertThat(capabilities.perPortMetering()).isTrue();
        assertThat(capabilities.bmsHandshake()).isFalse();
        assertThat(capabilities.portIdModel()).isEqualTo(ProtocolCapabilities.PortIdModel.PORT_NUMBER);
        assertThat(capabilities.maxPorts()).isEqualTo(12);
        assertThat(adapter.protocol()).isEqualTo(BalanMqttAdapter.PROTOCOL);
    }

    private static PlatformEvent only(List<PlatformEvent> events) {
        assertThat(events).hasSize(1);
        return events.get(0);
    }
}
