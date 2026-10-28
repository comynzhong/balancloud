package com.balancloud.device.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 只活在测试里的第三种协议：竖线 + 分号分隔的纯文本帧，与 BALAN_MQTT 的 JSON 帧毫无共同点。
 *
 * <p>它存在的唯一目的，是证明「新增协议不改 core 业务代码」这条缝是真的：
 * 注册它之后，{@code charger.protocol='TEST_PROTOCOL'} 的设备要走完整上行链路
 * （影子、告警、订单）与下行指令，而订单、计费、告警代码一行都不用动。
 */
class TestProtocolAdapter implements DeviceProtocolAdapter {

    static final String PROTOCOL = "TEST_PROTOCOL";

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public ProtocolCapabilities capabilities() {
        return new ProtocolCapabilities(true, true, false, true, false,
                false, false, 2, ProtocolCapabilities.PortIdModel.GUN_NUMBER);
    }

    @Override
    public List<PlatformEvent> decode(RawFrame frame) {
        try {
            return decodeText(frame.bodyAsText());
        } catch (RuntimeException e) {
            return List.of(new PlatformEvent.Unrecognized(deviceNoOf(frame.bodyAsText()), e.getMessage()));
        }
    }

    @Override
    public RawFrame encode(PlatformCommand command) {
        StringBuilder body = new StringBuilder();
        if (PlatformCommand.ACTION_OTA.equals(command.action())) {
            body.append("OTA|").append(command.deviceNo());
            for (Map.Entry<String, Object> entry : command.params().entrySet()) {
                body.append('|').append(entry.getKey()).append('=').append(entry.getValue());
            }
        } else {
            body.append("CMD|").append(command.cmdId()).append('|').append(command.deviceNo())
                    .append('|').append(command.portNo()).append('|')
                    .append(command.type() == null ? "" : command.type().name());
            for (Map.Entry<String, Object> entry : command.params().entrySet()) {
                body.append('|').append(entry.getKey()).append('=').append(entry.getValue());
            }
        }
        return new RawFrame("mqtt", "test/down/" + command.deviceNo() + "/cmd",
                body.toString().getBytes(StandardCharsets.UTF_8), Map.of(), java.time.Instant.now());
    }

    private List<PlatformEvent> decodeText(String text) {
        if (text == null || text.isBlank()) {
            return List.of(new PlatformEvent.Unrecognized("", "empty frame"));
        }
        String[] parts = text.split("\\|", -1);
        String kind = parts[0];
        String deviceNo = parts.length > 1 ? parts[1] : "";
        switch (kind) {
            case "ONLINE" -> {
                return List.of(new PlatformEvent.Connectivity(deviceNo,
                        parts.length > 3 ? "1".equals(parts[3]) : true, longAt(parts, 2)));
            }
            case "METRIC" -> {
                long ts = longAt(parts, 2);
                List<PortSample> ports = new ArrayList<>();
                for (String chunk : parts.length > 3 ? parts[3].split(";") : new String[0]) {
                    if (chunk.isBlank()) {
                        continue;
                    }
                    String[] f = chunk.split(",", -1);
                    ports.add(PortSample.of(intAt(f, 0), f.length > 1 ? f[1] : null,
                            doubleAt(f, 2), doubleAt(f, 3), doubleAt(f, 4), doubleAt(f, 5),
                            doubleAt(f, 6), intAt(f, 7), boolAt(f, 8)));
                }
                return ports.isEmpty() ? List.of()
                        : List.of(new PlatformEvent.MetricReport(deviceNo, ts, ports));
            }
            case "ALERT" -> {
                AlertHint hint = new AlertHint(parts.length > 3 ? parts[3] : "UNKNOWN",
                        parts.length > 3 ? parts[3] : "", parts.length > 4 ? parts[4] : "MAJOR", true);
                return List.of(new PlatformEvent.VendorAlert(deviceNo,
                        parts.length > 2 ? intAt(parts, 2) : null, hint,
                        parts.length > 3 ? parts[3] : "UNKNOWN",
                        parts.length > 5 ? parts[5] : "", doubleAt(parts, 6)));
            }
            case "ACK" -> {
                boolean success = parts.length > 5 && "SUCCESS".equals(parts[5]);
                return List.of(new PlatformEvent.CommandAck(deviceNo, parts.length > 2 ? parts[2] : "",
                        parts.length > 3 ? intAt(parts, 3) : null, parts.length > 4 ? parts[4] : "",
                        success, parts.length > 5 ? parts[5] : "", parts.length > 6 ? parts[6] : "",
                        longAt(parts, 7)));
            }
            case "ORDER" -> {
                return List.of(new PlatformEvent.OrderLifecycle(deviceNo,
                        parts.length > 2 ? intAt(parts, 2) : null,
                        parts.length > 3 ? parts[3] : null,
                        parts.length > 4 ? parts[4] : null,
                        doubleAt(parts, 5), longAt(parts, 6)));
            }
            default -> {
                return List.of(new PlatformEvent.Unrecognized(deviceNo, "unknown frame kind: " + kind));
            }
        }
    }

    private static String deviceNoOf(String text) {
        String[] parts = text == null ? new String[0] : text.split("\\|", -1);
        return parts.length > 1 ? parts[1] : "";
    }

    private static long longAt(String[] parts, int index) {
        Long value = index < parts.length ? parseLong(parts[index]) : null;
        return value == null ? 0L : value;
    }

    private static Long parseLong(String text) {
        try {
            return text == null || text.isBlank() ? null : Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intAt(String[] parts, int index) {
        Long value = parseLong(index < parts.length ? parts[index] : null);
        return value == null ? null : value.intValue();
    }

    private static Double doubleAt(String[] parts, int index) {
        try {
            return index >= parts.length || parts[index].isBlank() ? null : Double.valueOf(parts[index].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean boolAt(String[] parts, int index) {
        if (index >= parts.length || parts[index].isBlank()) {
            return null;
        }
        return "1".equals(parts[index].trim());
    }
}
