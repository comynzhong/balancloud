package com.balancloud.device.protocol;

import com.balancloud.command.model.CommandType;
import com.balancloud.device.ocpp.domain.OcppTransaction;
import com.balancloud.device.ocpp.service.OcppTransactionStore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OCPP 1.6J 的语义层单测：只验「报文 → 平台事件」和「平台指令 → CALL」，
 * 不碰 WebSocket 与会话。单位换算是重点——Wh 不出适配器。
 */
class Ocpp16JAdapterTest {

    private static final String DEVICE = "CP-DC-001";
    private static final long TS = 1762000000000L;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final OcppTransactionStore transactionStore = mock(OcppTransactionStore.class);
    private final Ocpp16JAdapter adapter = new Ocpp16JAdapter(objectMapper, transactionStore);

    private RawFrame call(String action, String payload) {
        String text = "[2,\"u-1\",\"" + action + "\"," + payload + "]";
        return new RawFrame("ocpp", DEVICE, text.getBytes(StandardCharsets.UTF_8),
                Map.of("ocpp.uniqueId", "u-1", "ocpp.action", action), Instant.ofEpochMilli(TS));
    }

    private static PlatformEvent only(List<PlatformEvent> events) {
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    @Test
    void bootAndHeartbeatBothMeanOnline() {
        PlatformEvent.Connectivity boot = (PlatformEvent.Connectivity) only(adapter.decode(call(
                "BootNotification", """
                        {"chargePointVendor":"Balan","chargePointModel":"DC-120"}""")));
        assertThat(boot.deviceNo()).isEqualTo(DEVICE);
        assertThat(boot.online()).isTrue();
        assertThat(boot.ts()).isEqualTo(TS);

        PlatformEvent.Connectivity beat = (PlatformEvent.Connectivity) only(adapter.decode(call(
                "Heartbeat", "{}")));
        assertThat(beat.online()).isTrue();
    }

    @Test
    void statusNotificationUsesThePlatformFourStateVocabulary() {
        assertThat(status("Available")).isEqualTo("IDLE");
        assertThat(status("Preparing")).isEqualTo("OCCUPIED");
        assertThat(status("Charging")).isEqualTo("CHARGING");
        assertThat(status("Faulted")).isEqualTo("FAULT");
        // 交易结束但枪还锁着：必须保持插枪，否则结算时占位判定拿不到信号
        assertThat(status("Finishing")).isEqualTo("OCCUPIED");
        assertThat(status("SuspendedEV")).isEqualTo("OCCUPIED");
        // 平台没有 Unavailable 这个词，归 IDLE，由在线状态与告警表达
        assertThat(status("Unavailable")).isEqualTo("IDLE");
    }

    @Test
    void statusNotificationKeepsConnectorAndErrorCode() {
        PlatformEvent.PortStatus event = (PlatformEvent.PortStatus) only(adapter.decode(call(
                "StatusNotification", """
                        {"connectorId":2,"status":"Faulted","errorCode":"GroundFailure",
                         "timestamp":"2026-10-27T10:00:00+08:00"}""")));
        assertThat(event.connectorId()).isEqualTo(2);
        assertThat(event.status()).isEqualTo("FAULT");
        assertThat(event.errorCode()).isEqualTo("GroundFailure");
        assertThat(event.ts()).isEqualTo(Instant.parse("2026-10-27T02:00:00Z").toEpochMilli());
    }

    @Test
    void startTransactionConvertsMeterStartFromWhToKwh() {
        PlatformEvent.TransactionStarted event = (PlatformEvent.TransactionStarted) only(adapter.decode(call(
                "StartTransaction", """
                        {"connectorId":1,"idTag":"BALAN","meterStart":1200,
                         "timestamp":"2026-10-27T10:00:00+08:00"}""")));
        assertThat(event.connectorId()).isEqualTo(1);
        assertThat(event.idTag()).isEqualTo("BALAN");
        assertThat(event.meterStartKwh()).isEqualTo(1.2);
    }

    @Test
    void meterValuesCarryDcSignalsAndStopUsingWhSuffix() {
        PlatformEvent.MetricReport report = (PlatformEvent.MetricReport) only(adapter.decode(call(
                "MeterValues", """
                        {"connectorId":1,"transactionId":42,"meterValue":[
                          {"timestamp":"2026-10-27T10:05:00+08:00","sampledValue":[
                            {"measurand":"Energy.Active.Import.Register","value":"3600","unit":"Wh"},
                            {"measurand":"Power.Active.Import","value":"60000","unit":"W"},
                            {"measurand":"Voltage","value":"750.5","unit":"V"},
                            {"measurand":"Current.Import","value":"80","unit":"A"},
                            {"measurand":"Temperature","value":"41","unit":"Celsius"},
                            {"measurand":"SoC","value":"67","unit":"Percent"}]}]}""")));

        assertThat(report.ts()).isEqualTo(Instant.parse("2026-10-27T02:05:00Z").toEpochMilli());
        PortSample port = report.ports().get(0);
        assertThat(port.portNo()).isEqualTo(1);
        assertThat(port.energyTotal()).isEqualTo(3.6);
        assertThat(port.powerKw()).isEqualTo(60.0);
        assertThat(port.voltage()).isEqualTo(750.5);
        assertThat(port.currentA()).isEqualTo(80.0);
        assertThat(port.temperature()).isEqualTo(41.0);
        assertThat(port.soc()).isEqualTo(67);
        // 有 transactionId 才算「正在充电」，否则只上报读数不改状态
        assertThat(port.status()).isEqualTo("CHARGING");
        assertThat(port.plugged()).isTrue();
    }

    @Test
    void meterValuesWithoutTransactionDoNotClaimCharging() {
        PlatformEvent.MetricReport report = (PlatformEvent.MetricReport) only(adapter.decode(call(
                "MeterValues", """
                        {"connectorId":1,"meterValue":[{"sampledValue":[
                          {"measurand":"Voltage","value":"750"}]}]}""")));
        PortSample port = report.ports().get(0);
        assertThat(port.status()).isNull();
        assertThat(port.plugged()).isNull();
    }

    @Test
    void stopTransactionConvertsMeterStopAndMapsTheReason() {
        PlatformEvent.TransactionStopped event = (PlatformEvent.TransactionStopped) only(adapter.decode(call(
                "StopTransaction", """
                        {"transactionId":42,"meterStop":600,"reason":"EVDisconnected",
                         "timestamp":"2026-10-27T10:30:00+08:00"}""")));
        assertThat(event.transactionId()).isEqualTo(42L);
        assertThat(event.meterStopKwh()).isEqualTo(0.6);
        assertThat(event.stopReason()).isEqualTo("USER_STOP");
        assertThat(event.rawStopReason()).isEqualTo("EVDisconnected");
        // 1.6J 的 StopTransaction 不带 connectorId，适配器必须留空交给上层回查
        assertThat(event.connectorId()).isZero();
        assertThat(event.ts()).isEqualTo(Instant.parse("2026-10-27T02:30:00Z").toEpochMilli());
    }

    @Test
    void stopTransactionHonorsConnectorWhenTheChargerSendsOne() {
        PlatformEvent.TransactionStopped event = (PlatformEvent.TransactionStopped) only(adapter.decode(call(
                "StopTransaction", """
                        {"connectorId":2,"transactionId":7,"meterStop":0}""")));
        assertThat(event.connectorId()).isEqualTo(2);
    }

    @Test
    void emergencyStopIsAFaultNotAUserStop() {
        PlatformEvent.TransactionStopped event = (PlatformEvent.TransactionStopped) only(adapter.decode(call(
                "StopTransaction", """
                        {"transactionId":7,"meterStop":0,"reason":"EmergencyStop"}""")));
        assertThat(event.stopReason()).isEqualTo("FAULT");
        assertThat(event.rawStopReason()).isEqualTo("EmergencyStop");
    }

    @Test
    void frameThatIsNotACallIsReportedInsteadOfSilentlyDropped() {
        RawFrame callResult = new RawFrame("ocpp", DEVICE,
                "[3,\"u-1\",{}]".getBytes(StandardCharsets.UTF_8), Map.of(), Instant.now());
        assertThat(only(adapter.decode(callResult)))
                .isInstanceOf(PlatformEvent.Unrecognized.class)
                .extracting(e -> ((PlatformEvent.Unrecognized) e).reason()).isEqualTo("不是 OCPP CALL");

        RawFrame broken = new RawFrame("ocpp", DEVICE,
                "{oops".getBytes(StandardCharsets.UTF_8), Map.of(), Instant.now());
        assertThat(only(adapter.decode(broken)))
                .extracting(e -> ((PlatformEvent.Unrecognized) e).reason()).isEqualTo("OCPP 帧不是合法 JSON");

        RawFrame foreign = RawFrame.mqtt("balancloud/up/x/metric", "{}");
        assertThat(only(adapter.decode(foreign)))
                .extracting(e -> ((PlatformEvent.Unrecognized) e).reason()).isEqualTo("不是 OCPP 通道的帧: mqtt");
    }

    @Test
    void unknownActionIsUnrecognizedRatherThanIgnoredSilently() {
        PlatformEvent.Unrecognized event = (PlatformEvent.Unrecognized) only(adapter.decode(call(
                "DataTransfer", "{}")));
        assertThat(event.reason()).isEqualTo("未处理的 OCPP 动作 DataTransfer");
    }

    @Test
    void startCommandEncodesRemoteStartWithConnectorAndPendingHeaders() {
        RawFrame frame = adapter.encode(PlatformCommand.control("cmd-1", DEVICE, 2,
                CommandType.START, Map.of()));

        assertThat(frame.channel()).isEqualTo("ocpp");
        assertThat(frame.topicOrPeer()).isEqualTo(DEVICE);
        assertThat(frame.headers()).containsEntry("ocpp.type", "START")
                .containsEntry("ocpp.cmdId", "cmd-1")
                .containsEntry("ocpp.portNo", "2");
        assertThat(frame.headers().get("ocpp.uniqueId")).isNotBlank();

        var body = readBody(frame);
        assertThat(body.path(0).asInt()).isEqualTo(2);
        assertThat(body.path(1).asText()).isEqualTo(frame.headers().get("ocpp.uniqueId"));
        assertThat(body.path(2).asText()).isEqualTo("RemoteStartTransaction");
        assertThat(body.path(3).path("connectorId").asInt()).isEqualTo(2);
        assertThat(body.path(3).path("idTag").asText()).isEqualTo("BALAN");
    }

    @Test
    void stopCommandUsesTheActiveTransactionId() {
        OcppTransaction tx = new OcppTransaction();
        tx.setId(88L);
        when(transactionStore.findActiveByOrderNo("ORD-1")).thenReturn(tx);

        RawFrame frame = adapter.encode(new PlatformCommand(PlatformCommand.ACTION_CMD, "cmd-2",
                DEVICE, 1, CommandType.STOP, Map.of("orderNo", "ORD-1"), TS));

        var body = readBody(frame);
        assertThat(body.path(2).asText()).isEqualTo("RemoteStopTransaction");
        assertThat(body.path(3).path("transactionId").asLong()).isEqualTo(88L);
    }

    @Test
    void stopWithoutTransactionIsNotSentAsAFakeCall() {
        RawFrame frame = adapter.encode(new PlatformCommand(PlatformCommand.ACTION_CMD, "cmd-3",
                DEVICE, 1, CommandType.STOP, Map.of("orderNo", "ORD-none"), TS));

        assertThat(frame.body()).isEmpty();
        assertThat(frame.headers()).containsKey("ocpp.error");
    }

    @Test
    void thresholdAndOtaAreExplicitlyUnsupported() {
        RawFrame threshold = adapter.encode(PlatformCommand.control("cmd-4", DEVICE, 1,
                CommandType.SET_THRESHOLD, Map.of()));
        assertThat(threshold.body()).isEmpty();
        assertThat(threshold.headers().get("ocpp.error")).contains("不支持指令");

        RawFrame ota = adapter.encode(PlatformCommand.ota(DEVICE, Map.of("version", "1.0.1")));
        assertThat(ota.body()).isEmpty();
        assertThat(ota.headers().get("ocpp.error")).contains("不下发固件");
    }

    @Test
    void dedupKeyIsPerChargePointAndUniqueId() {
        RawFrame frame = call("Heartbeat", "{}");
        assertThat(adapter.dedupKey(frame)).contains("ocpp:" + DEVICE + ":u-1");

        RawFrame noId = new RawFrame("ocpp", DEVICE, "[2,\"\"]".getBytes(StandardCharsets.UTF_8),
                Map.of(), Instant.now());
        assertThat(adapter.dedupKey(noId)).isEmpty();
        assertThat(adapter.dedupKey(RawFrame.mqtt("balancloud/up/x/metric", "{}"))).isEmpty();
    }

    private String status(String ocppStatus) {
        PlatformEvent.PortStatus event = (PlatformEvent.PortStatus) only(adapter.decode(call(
                "StatusNotification", "{\"connectorId\":1,\"status\":\"" + ocppStatus + "\"}")));
        return event.status();
    }

    private com.fasterxml.jackson.databind.JsonNode readBody(RawFrame frame) {
        try {
            return objectMapper.readTree(frame.body());
        } catch (Exception e) {
            throw new AssertionError("下行帧不是合法 JSON: " + frame.bodyAsText(), e);
        }
    }
}
