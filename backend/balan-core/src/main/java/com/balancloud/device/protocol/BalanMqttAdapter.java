package com.balancloud.device.protocol;

import com.balancloud.alert.model.AlertType;
import com.balancloud.mqtt.model.AlertPayload;
import com.balancloud.mqtt.model.MetricPayload;
import com.balancloud.mqtt.model.PortMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置 BALAN_MQTT 协议的语义层实现。
 *
 * <p>本类的每一段解码都是从 {@code DeviceMessageHandler} 与 {@code CommandService} 原样搬来的，
 * 字段名、主题、默认值、缺字段时的回落全部保持不动——这一步只改「代码住在哪个类里」，
 * 不改「代码做什么」。行为差异会在 §46 Step 7 的行为 diff 里暴露出来，所以现在不允许有差异。
 *
 * <p>由 {@link ProtocolAutoConfiguration} 装配成 bean，{@code UplinkFrameRouter} 按
 * {@code charger.protocol} 选到它做上行解码，{@code CommandService} 用它做下行编码。
 */
public class BalanMqttAdapter implements DeviceProtocolAdapter {

    public static final String PROTOCOL = "BALAN_MQTT";

    private static final Logger log = LoggerFactory.getLogger(BalanMqttAdapter.class);
    private static final String UPLINK_PREFIX = "balancloud/up/";
    private static final String DOWNLINK_PREFIX = "balancloud/down/";

    /** 内置协议：四类控制指令都有、每路独立计量、有插座级 plugged，但没有 BMS 握手。 */
    private static final ProtocolCapabilities CAPABILITIES = new ProtocolCapabilities(
            true, true, true, true, true, true, false, 12, ProtocolCapabilities.PortIdModel.PORT_NUMBER);

    private final ObjectMapper objectMapper;

    public BalanMqttAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public ProtocolCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public List<PlatformEvent> decode(RawFrame frame) {
        String[] parts = frame.topicOrPeer() == null ? new String[0] : frame.topicOrPeer().split("/");
        if (frame.topicOrPeer() == null || !frame.topicOrPeer().startsWith(UPLINK_PREFIX) || parts.length < 4) {
            return List.of(new PlatformEvent.Unrecognized("", "上行主题不是 " + UPLINK_PREFIX + "{deviceNo}/{event}"));
        }
        String deviceNo = parts[2];
        String event = parts[3];
        String payload = frame.bodyAsText();
        return switch (event) {
            case "online" -> decodeOnline(deviceNo, payload, frame.receivedAt());
            case "metric" -> decodeMetric(deviceNo, payload);
            case "alert" -> decodeAlert(deviceNo, payload);
            case "cmd_ack" -> decodeCommandAck(deviceNo, payload);
            case "order_event" -> decodeOrderEvent(deviceNo, payload);
            default -> List.of(new PlatformEvent.Unrecognized(deviceNo, "未知事件 " + event));
        };
    }

    @Override
    public RawFrame encode(PlatformCommand command) {
        boolean ota = PlatformCommand.ACTION_OTA.equals(command.action());
        String topic = DOWNLINK_PREFIX + command.deviceNo() + "/" + (ota ? PlatformCommand.ACTION_OTA
                : PlatformCommand.ACTION_CMD);
        Map<String, Object> payload = ota ? new LinkedHashMap<>(command.params()) : commandEnvelope(command);
        return new RawFrame("mqtt", topic, jsonBytes(payload), Map.of(), Instant.now());
    }

    private Map<String, Object> commandEnvelope(PlatformCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cmdId", command.cmdId());
        payload.put("deviceId", command.deviceNo());
        payload.put("portNo", command.portNo());
        payload.put("type", command.type() == null ? null : command.type().name());
        payload.put("params", command.params());
        payload.put("ts", command.ts());
        return payload;
    }

    private byte[] jsonBytes(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to encode downlink payload: {}", e.getMessage());
            return new byte[0];
        }
    }

    /** 原 {@code DeviceMessageHandler.handleOnline} 里的报文部分：解不出 online 仍按上线处理。 */
    private List<PlatformEvent> decodeOnline(String deviceNo, String payload, Instant receivedAt) {
        boolean online = true;
        long ts = receivedAt.toEpochMilli();
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("online")) {
                online = root.path("online").asBoolean(true);
            }
            if (root.has("ts")) {
                ts = root.path("ts").asLong(ts);
            }
        } catch (Exception e) {
            log.warn("Invalid online payload from {}: {}", deviceNo, payload);
        }
        return List.of(new PlatformEvent.Connectivity(deviceNo, online, ts));
    }

    /** 原 {@code DeviceMessageHandler.handleMetric} 里的绑定与时间戳兜底，逐端口转成 PortSample。 */
    private List<PlatformEvent> decodeMetric(String deviceNo, String payload) {
        MetricPayload metricPayload;
        try {
            metricPayload = objectMapper.readValue(payload, MetricPayload.class);
        } catch (Exception e) {
            log.warn("Invalid metric payload from {}: {}", deviceNo, e.getMessage());
            return List.of();
        }
        if (metricPayload.getPorts() == null || metricPayload.getPorts().isEmpty()) {
            return List.of();
        }
        long ts = metricPayload.getTs() == null || metricPayload.getTs() <= 0
                ? System.currentTimeMillis()
                : metricPayload.getTs();
        List<PortSample> samples = new ArrayList<>(metricPayload.getPorts().size());
        for (PortMetric metric : metricPayload.getPorts()) {
            samples.add(toSample(metric));
        }
        return List.of(new PlatformEvent.MetricReport(deviceNo, ts, samples));
    }

    private PortSample toSample(PortMetric metric) {
        return PortSample.of(metric.getPortNo(), metric.getStatus(), metric.getVoltage(),
                metric.getCurrentA(), metric.getTemperature(), metric.getPowerKw(),
                metric.getEnergyTotal(), metric.getSignalStrength(), metric.getPlugged());
    }

    /**
     * 原 {@code DeviceMessageHandler.handleDeviceAlert} 里的厂商类型映射：
     * 映射不上时按端口故障处理，但把厂商原文与「没映射上」这件事一起交出去。
     */
    private List<PlatformEvent> decodeAlert(String deviceNo, String payload) {
        AlertPayload alertPayload;
        try {
            alertPayload = objectMapper.readValue(payload, AlertPayload.class);
        } catch (Exception e) {
            log.warn("Invalid alert payload from {}: {}", deviceNo, payload);
            return List.of();
        }
        AlertType type = AlertType.fromVendor(alertPayload.getType());
        String message = StringUtils.hasText(alertPayload.getMessage())
                ? alertPayload.getMessage()
                : type.getLabel();
        if (!StringUtils.hasText(alertPayload.getType()) || AlertType.parse(alertPayload.getType()) == null) {
            message = message + "（厂商类型 " + alertPayload.getType() + "）";
        }
        AlertHint hint = new AlertHint(alertPayload.getType(), type.name(), alertPayload.getLevel(),
                AlertType.parse(alertPayload.getType()) != null);
        return List.of(new PlatformEvent.VendorAlert(deviceNo, alertPayload.getPortNo(), hint,
                alertPayload.getType(), message, alertPayload.getValue()));
    }

    /** 原 {@code CommandService.handleAck} 里的报文部分；cmdId 缺失、指令未知的判断仍留在指令状态机。 */
    private List<PlatformEvent> decodeCommandAck(String deviceNo, String payload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Invalid cmd_ack from {}: {}", deviceNo, payload);
            return List.of();
        }
        String rawResult = root.path("result").asText("FAILED");
        boolean success = "SUCCESS".equalsIgnoreCase(rawResult);
        return List.of(new PlatformEvent.CommandAck(deviceNo,
                root.path("cmdId").asText(null),
                root.hasNonNull("portNo") ? root.path("portNo").asInt() : null,
                root.path("type").asText(null),
                success,
                rawResult,
                root.path("message").asText(""),
                root.path("ts").asLong(System.currentTimeMillis())));
    }

    /** 原 {@code ChargingService.handleOrderEvent} 里的报文部分；停止原因码保持原文，由订单侧解释。 */
    private List<PlatformEvent> decodeOrderEvent(String deviceNo, String payload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Invalid order_event from {}: {}", deviceNo, payload);
            return List.of();
        }
        return List.of(new PlatformEvent.OrderLifecycle(deviceNo,
                root.hasNonNull("portNo") ? root.path("portNo").asInt() : null,
                root.path("orderNo").asText(null),
                root.path("stopReason").asText(null),
                root.hasNonNull("energyTotal") ? root.path("energyTotal").asDouble() : null,
                root.path("ts").asLong(System.currentTimeMillis())));
    }
}
