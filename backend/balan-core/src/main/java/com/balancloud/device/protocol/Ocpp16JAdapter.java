package com.balancloud.device.protocol;

import com.balancloud.command.model.CommandType;
import com.balancloud.device.ocpp.OcppProtocol;
import com.balancloud.device.ocpp.domain.OcppTransaction;
import com.balancloud.device.ocpp.service.OcppTransactionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OCPP 1.6J 的语义层：CALL 帧 ↔ 平台事件/指令。
 *
 * <p>只负责「消息里是什么」，不负责发回执与待回执映射——那些是 {@code OcppFrameService}
 * 的传输职责。这样同一套解码可以脱离 WebSocket 单测。
 *
 * <p>单位在这一层统一：OCPP 的电表是 Wh，平台计费是 kWh；Wh 不往下游传。
 */
public class Ocpp16JAdapter implements DeviceProtocolAdapter {

    public static final String PROTOCOL = OcppProtocol.PROTOCOL;

    private static final Logger log = LoggerFactory.getLogger(Ocpp16JAdapter.class);

    /** 直流桩：能远控启停与解锁、每枪独立计量、有 BMS 握手；不支持阈值配置与 OTA。 */
    private static final ProtocolCapabilities CAPABILITIES = new ProtocolCapabilities(
            true, true, true, false, false, true, true,
            2, ProtocolCapabilities.PortIdModel.CONNECTOR_ID);

    private final ObjectMapper objectMapper;
    private final OcppTransactionStore transactionStore;

    public Ocpp16JAdapter(ObjectMapper objectMapper, OcppTransactionStore transactionStore) {
        this.objectMapper = objectMapper;
        this.transactionStore = transactionStore;
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
        if (!"ocpp".equals(frame.channel())) {
            return List.of(new PlatformEvent.Unrecognized("", "不是 OCPP 通道的帧: " + frame.channel()));
        }
        String deviceNo = frame.topicOrPeer();
        JsonNode root;
        try {
            root = objectMapper.readTree(frame.body());
        } catch (Exception e) {
            return List.of(new PlatformEvent.Unrecognized(deviceNo, "OCPP 帧不是合法 JSON"));
        }
        if (!root.isArray() || root.path(0).asInt(-1) != 2) {
            // CALLRESULT/CALLERROR 由 OcppFrameService 处理，不进上行路由。
            return List.of(new PlatformEvent.Unrecognized(deviceNo, "不是 OCPP CALL"));
        }
        String action = root.path(2).asText("");
        JsonNode payload = root.size() > 3 ? root.get(3) : objectMapper.createObjectNode();
        long now = frame.receivedAt() == null ? System.currentTimeMillis() : frame.receivedAt().toEpochMilli();
        return switch (action) {
            case "BootNotification", "Heartbeat" ->
                    List.of(new PlatformEvent.Connectivity(deviceNo, true, now));
            case "StatusNotification" -> decodeStatus(deviceNo, payload, now);
            case "Authorize" -> List.of();
            case "StartTransaction" -> decodeStart(deviceNo, payload, now);
            case "MeterValues" -> decodeMeterValues(deviceNo, payload, now);
            case "StopTransaction" -> decodeStop(deviceNo, payload, now);
            default -> List.of(new PlatformEvent.Unrecognized(deviceNo, "未处理的 OCPP 动作 " + action));
        };
    }

    @Override
    public RawFrame encode(PlatformCommand command) {
        String identity = command.deviceNo();
        String uniqueId = UUID.randomUUID().toString();
        String action;
        ObjectNode payload = objectMapper.createObjectNode();
        if (PlatformCommand.ACTION_OTA.equals(command.action())) {
            return unsupported(identity, command, "OCPP 1.6J 本批不下发固件");
        }
        CommandType type = command.type();
        if (type == null) {
            return unsupported(identity, command, "指令类型为空");
        }
        switch (type) {
            case START -> {
                action = "RemoteStartTransaction";
                payload.put("idTag", "BALAN");
                payload.put("connectorId", command.portNo() == null ? 1 : command.portNo());
            }
            case STOP -> {
                action = "RemoteStopTransaction";
                OcppTransaction transaction = transactionStore.findActiveByOrderNo(stringParam(command, "orderNo"));
                if (transaction == null) {
                    return unsupported(identity, command, "找不到进行中的 OCPP 交易");
                }
                payload.put("transactionId", transaction.getId().intValue());
            }
            case UNLOCK -> {
                action = "UnlockConnector";
                payload.put("connectorId", command.portNo() == null ? 1 : command.portNo());
            }
            default -> {
                return unsupported(identity, command, "OCPP 1.6J 不支持指令 " + type);
            }
        }
        ArrayNode call = objectMapper.createArrayNode();
        call.add(2).add(uniqueId).add(action).add(payload);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("ocpp.uniqueId", uniqueId);
        headers.put("ocpp.type", type.name());
        if (command.cmdId() != null) {
            headers.put("ocpp.cmdId", command.cmdId());
        }
        if (command.portNo() != null) {
            headers.put("ocpp.portNo", command.portNo().toString());
        }
        return new RawFrame("ocpp", identity, call.toString().getBytes(StandardCharsets.UTF_8),
                headers, Instant.now());
    }

    @Override
    public Optional<String> dedupKey(RawFrame frame) {
        if (!"ocpp".equals(frame.channel())) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(frame.body());
            String uniqueId = root.path(1).asText("");
            return StringUtils.hasText(uniqueId)
                    ? Optional.of("ocpp:" + frame.topicOrPeer() + ":" + uniqueId)
                    : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private List<PlatformEvent> decodeStatus(String deviceNo, JsonNode payload, long now) {
        int connectorId = payload.path("connectorId").asInt(0);
        String status = mapPortStatus(payload.path("status").asText(""));
        String errorCode = payload.path("errorCode").asText("");
        long ts = parseTime(payload.path("timestamp").asText(""), now);
        return List.of(new PlatformEvent.PortStatus(deviceNo, connectorId, status, errorCode, ts));
    }

    private List<PlatformEvent> decodeStart(String deviceNo, JsonNode payload, long now) {
        int connectorId = payload.path("connectorId").asInt(1);
        String idTag = payload.path("idTag").asText("");
        double meterStartKwh = payload.path("meterStart").asDouble(0) / 1000.0;
        long ts = parseTime(payload.path("timestamp").asText(""), now);
        return List.of(new PlatformEvent.TransactionStarted(deviceNo, connectorId, idTag, meterStartKwh, ts));
    }

    private List<PlatformEvent> decodeMeterValues(String deviceNo, JsonNode payload, long now) {
        int connectorId = payload.path("connectorId").asInt(1);
        int transactionId = payload.path("transactionId").asInt(0);
        Double energyKwh = null;
        Double powerKw = null;
        Double voltage = null;
        Double currentA = null;
        Double temperature = null;
        Integer soc = null;
        long ts = now;
        for (JsonNode meterValue : payload.path("meterValue")) {
            long sampleTs = parseTime(meterValue.path("timestamp").asText(""), ts);
            if (sampleTs > 0) {
                ts = sampleTs;
            }
            for (JsonNode sample : meterValue.path("sampledValue")) {
                String measurand = sample.path("measurand").asText("Energy.Active.Import.Register");
                double value = sample.path("value").asDouble(Double.NaN);
                if (Double.isNaN(value)) {
                    continue;
                }
                String unit = sample.path("unit").asText("");
                switch (measurand) {
                    case "Energy.Active.Import.Register" ->
                            energyKwh = "kWh".equalsIgnoreCase(unit) ? value : value / 1000.0;
                    case "Power.Active.Import" ->
                            powerKw = "kW".equalsIgnoreCase(unit) ? value : value / 1000.0;
                    case "Voltage" -> voltage = value;
                    case "Current.Import" -> currentA = value;
                    case "Temperature" -> temperature = value;
                    case "SoC" -> soc = (int) Math.round(value);
                    default -> {
                        // 其它 measurand 本批不消费，留在原始通信日志里。
                    }
                }
            }
        }
        PortSample sample = new PortSample(connectorId,
                transactionId > 0 ? "CHARGING" : null,
                voltage, currentA, temperature, powerKw, energyKwh,
                null, transactionId > 0 ? Boolean.TRUE : null,
                soc, null, null, null, connectorId, connectorId, null, null, null);
        return List.of(new PlatformEvent.MetricReport(deviceNo, ts, List.of(sample)));
    }

    private List<PlatformEvent> decodeStop(String deviceNo, JsonNode payload, long now) {
        // OCPP 1.6J 的 StopTransaction 不带 connectorId，只能靠 transactionId 回查映射；
        // 个别桩会额外带上，那就尊重它，没带时留 0 交给上层解析。
        int connectorId = payload.path("connectorId").asInt(0);
        long transactionId = payload.path("transactionId").asLong(0);
        double meterStopKwh = payload.path("meterStop").asDouble(0) / 1000.0;
        String rawReason = payload.path("reason").asText("");
        long ts = parseTime(payload.path("timestamp").asText(""), now);
        return List.of(new PlatformEvent.TransactionStopped(deviceNo, connectorId, transactionId,
                meterStopKwh, mapStopReason(rawReason), rawReason, ts));
    }

    /**
     * OCPP 状态词汇 → 平台四值。只映射，不扩 {@code charger_port.status} 的词汇表（§45.7）。
     * Unavailable 在平台侧没有对应值，归 IDLE；桩不可用由在线状态与故障告警表达。
     * Finishing 是「交易结束但枪还锁在车上」，对应平台的 OCCUPIED，不能当 IDLE——
     * 否则 StopTransaction 结算时读不到插枪信号，占位费整条链路断掉。
     */
    static String mapPortStatus(String status) {
        return switch (status == null ? "" : status) {
            case "Charging" -> "CHARGING";
            case "Faulted" -> "FAULT";
            case "Preparing", "SuspendedEV", "SuspendedEVSE", "Reserved", "Finishing" -> "OCCUPIED";
            default -> "IDLE";
        };
    }

    /** OCPP stop reason → 平台停止原因；映射表单独可测，不散落在订单服务里。 */
    static String mapStopReason(String reason) {
        return switch (reason == null ? "" : reason) {
            case "EVDisconnected", "StoppedByEV", "Remote" -> "USER_STOP";
            case "EmergencyStop" -> "FAULT";
            case "Local", "SoftReset", "HardReset", "Reboot", "PowerLoss", "Other" -> "DEVICE_STOP";
            default -> "DEVICE_STOP";
        };
    }

    /** OCPP 时间戳带时区；解析失败用收到帧的时间兜底，不让一帧坏时间把整条消息丢掉。 */
    static long parseTime(String value, long fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            try {
                return Instant.parse(value).toEpochMilli();
            } catch (Exception e) {
                return fallback;
            }
        }
    }

    private String stringParam(PlatformCommand command, String key) {
        Object value = command.params().get(key);
        return value == null ? null : value.toString();
    }

    /**
     * 不支持的动作返回空正文帧：{@code DownlinkDispatcher} 会拒绝发送并记 warn，
     * 不伪造一条「已下发」的 CALL。
     */
    private RawFrame unsupported(String identity, PlatformCommand command, String reason) {
        log.warn("OCPP downlink skipped for {} ({}): {}", identity, command.cmdId(), reason);
        return new RawFrame("ocpp", identity, new byte[0], Map.of("ocpp.error", reason), Instant.now());
    }
}
