package com.balancloud.device.protocol;

import com.balancloud.command.model.CommandType;
import com.balancloud.device.service.SimulatorService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置协议对自己的一致性测试，外加一条「和仿真器对得上」的双向测试（§46 Step 3）。
 *
 * <p>最后那条是重点：平台里的设备端就是这个仿真器（918 行），它发的回执和它读的指令才是本协议的实况。
 * 只用测试里手写的报文自证，会把「平台自己的方言」当成「协议规范」写死——真接厂商设备时才发现对不上。
 */
class BalanMqttAdapterConformanceTest extends ProtocolAdapterConformanceTest {

    private static final String DEVICE = "CONF-DEV-0001";
    private static final String UPLINK = "balancloud/up/" + DEVICE + "/";

    private final ObjectMapper objectMapper =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final BalanMqttAdapter adapter = new BalanMqttAdapter(objectMapper);

    @Override
    protected DeviceProtocolAdapter adapter() {
        return adapter;
    }

    @Override
    protected String adapterMetricTopicPrefix() {
        return UPLINK;
    }

    @Override
    protected RawFrame metricFrame() {
        return RawFrame.mqtt(UPLINK + "metric",
                "{\"deviceId\":\"" + DEVICE + "\",\"ts\":1762000000000,\"ports\":[{\"portNo\":1,"
                        + "\"status\":\"CHARGING\",\"voltage\":220.5,\"currentA\":8.2,\"powerKw\":1.8,"
                        + "\"energyTotal\":24.9146,\"signalStrength\":-63,\"plugged\":true}]}");
    }

    @Override
    protected RawFrame ackFrame(String cmdId, boolean success) {
        return RawFrame.mqtt(UPLINK + "cmd_ack", json(SimulatorService.ackEnvelope(DEVICE, cmdId, 1, "START",
                success, "已开始", 1762000000000L)));
    }

    @Override
    protected PlatformCommand startCommand(String cmdId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orderNo", "CONF-ORDER-1");
        params.put("maxDurationMinutes", 480);
        return PlatformCommand.control(cmdId, DEVICE, 1, CommandType.START, params);
    }

    @Override
    protected RawFrame malformedMetricFrame() {
        return RawFrame.mqtt(UPLINK + "metric", "{这不是 JSON");
    }

    @Test
    void ackProducedByTheSimulatorDecodesBackToTheSameCommand() {
        // 设备端怎么回，是 SimulatorService.ackEnvelope 说了算，不是测试说了算
        Map<String, Object> envelope = SimulatorService.ackEnvelope(DEVICE, "CONF-CMD-9", 3, "STOP",
                false, "端口忙", 1762000000123L);
        PlatformEvent.CommandAck ack =
                (PlatformEvent.CommandAck) adapter().decode(RawFrame.mqtt(UPLINK + "cmd_ack", json(envelope))).get(0);

        assertThat(ack.deviceNo()).isEqualTo(DEVICE);
        assertThat(ack.cmdId()).isEqualTo("CONF-CMD-9");
        assertThat(ack.portNo()).isEqualTo(3);
        assertThat(ack.type()).isEqualTo("STOP");
        assertThat(ack.success()).isFalse();
        assertThat(ack.rawResult()).isEqualTo("FAILED");
        assertThat(ack.message()).isEqualTo("端口忙");
        assertThat(ack.ts()).isEqualTo(1762000000123L);
    }

    @Test
    void simulatorCanReadWhatTheAdapterEncodes() {
        // 仿真器读指令的方式（handleCommand）：cmdId 与 portNo 必须在，type 必须能解析成 CommandType
        RawFrame frame = adapter().encode(startCommand("CONF-CMD-7"));
        Map<String, Object> read = readDownlink(frame);

        assertThat(read.get("cmdId")).isEqualTo("CONF-CMD-7");
        assertThat(read.get("portNo")).isInstanceOf(Integer.class);
        assertThat(CommandType.parse(String.valueOf(read.get("type")))).isEqualTo(CommandType.START);
        assertThat(frame.topicOrPeer()).isEqualTo("balancloud/down/" + DEVICE + "/cmd");
    }

    @Test
    void decodedMetricCarriesEveryFieldTheSimulatorPublishes() {
        // 仿真器发的测点字段（SimulatorService.publishMetric 的 ports 项）必须全部解得出来
        RawFrame frame = metricFrame();
        PlatformEvent.MetricReport report = (PlatformEvent.MetricReport) adapter().decode(frame).get(0);
        PortSample sample = report.ports().get(0);

        assertThat(sample.portNo()).isEqualTo(1);
        assertThat(sample.status()).isEqualTo("CHARGING");
        assertThat(sample.voltage()).isEqualTo(220.5);
        assertThat(sample.currentA()).isEqualTo(8.2);
        assertThat(sample.powerKw()).isEqualTo(1.8);
        assertThat(sample.energyTotal()).isEqualTo(24.9146);
        assertThat(sample.signalStrength()).isEqualTo(-63);
        assertThat(sample.plugged()).isTrue();
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDownlink(RawFrame frame) {
        try {
            return objectMapper.readValue(frame.bodyAsText(), LinkedHashMap.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 注册表按协议名建索引，并且大小写不敏感（库里存的是大写，配置里可能写小写）。 */
    @Test
    void registryIndexesTheBuiltinProtocol() {
        List<DeviceProtocolAdapter> adapters = List.of(adapter);
        ProtocolAdapterRegistry registry = new ProtocolAdapterRegistry(adapters);

        assertThat(registry.available()).containsExactly(BalanMqttAdapter.PROTOCOL);
        assertThat(registry.require("balan_mqtt")).isSameAs(adapter);
        assertThat(registry.require(BalanMqttAdapter.PROTOCOL).capabilities().remoteStart()).isTrue();
    }
}
