package com.balancloud.device.protocol;

import java.time.Instant;
import java.util.Map;

/**
 * 传输无关的一帧。channel 目前有 "mqtt" 与 "ocpp" 两种，下行由 `DownlinkDispatcher` 按它选传输；
 * 纯 TCP 等后续 L0 传输继续在这个字段留位。
 *
 * <p>正文用 byte[] 而不是 String：二进制与带序号的厂商帧也要能进来，
 * 文本只是 MQTT 方言在这里的特例。
 */
public record RawFrame(String channel, String topicOrPeer, byte[] body,
                       Map<String, String> headers, Instant receivedAt) {

    public String bodyAsText() {
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static RawFrame mqtt(String topic, String payload) {
        return new RawFrame("mqtt", topic, payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of(), Instant.now());
    }
}
