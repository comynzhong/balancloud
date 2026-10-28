package com.balancloud.device.protocol;

import com.balancloud.device.ocpp.OcppDownlinkBus;
import com.balancloud.mqtt.MqttGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 下行总出口：按 {@link RawFrame#channel()} 选传输，业务侧不再直接摸 MQTT。
 *
 * <p>没有这一层时，OCPP 帧也会被塞进 {@code MqttGateway.publishRaw}，
 * 后者只能拒绝——指令看起来「发出去了」，实际永远到不了桩。
 */
@Service
public class DownlinkDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DownlinkDispatcher.class);

    private final MqttGateway mqttGateway;
    private final OcppDownlinkBus ocppDownlinkBus;

    public DownlinkDispatcher(MqttGateway mqttGateway, OcppDownlinkBus ocppDownlinkBus) {
        this.mqttGateway = mqttGateway;
        this.ocppDownlinkBus = ocppDownlinkBus;
    }

    public boolean send(RawFrame frame) {
        if (frame == null || !StringUtils.hasText(frame.topicOrPeer())) {
            return false;
        }
        if (frame.body() == null || frame.body().length == 0) {
            log.warn("Refusing to send empty {} frame to {}", frame.channel(), frame.topicOrPeer());
            return false;
        }
        String channel = frame.channel() == null ? "" : frame.channel();
        return switch (channel) {
            case "mqtt" -> mqttGateway.publishRaw(frame);
            case "ocpp" -> ocppDownlinkBus.send(frame);
            default -> {
                log.warn("Unknown downlink channel {} for {}", channel, frame.topicOrPeer());
                yield false;
            }
        };
    }
}
