package com.balancloud.mqtt.model;

import java.time.LocalDateTime;

public record MqttLogEntry(
        long id,
        LocalDateTime time,
        String direction,
        String topic,
        String deviceNo,
        String payload
) {
}
