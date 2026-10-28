package com.balancloud.device.protocol;

import com.balancloud.device.service.DeviceRouting;
import com.balancloud.device.service.DeviceTenantResolver;
import com.balancloud.mqtt.MqttDedupService;
import com.balancloud.mqtt.MqttLogService;
import com.balancloud.mqtt.event.UplinkMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 上行帧的唯一入口：记日志 → 定位租户与协议 → 幂等 → 解码 → 发布语义事件。
 *
 * <p>主题怎么切（第几段是设备号、第几段是事件名）属于 MQTT 传输，留在 {@code MqttGateway}；
 * 这里拿到的是已经切好的设备号、事件名和原始字节，不依赖 MQTT 的通道细节。
 *
 * <p>归属不明与时序：设备不存在或编号跨租户重名时直接丢弃并告警，不猜租户——
 * 猜错就是把设备写进别人的账本（§36 的多租户归属修复）。幂等在归属之后做，
 * 否则不带租户的去重键会让两个租户的同名设备互吞报文。
 */
@Service
public class UplinkFrameRouter {

    private static final Logger log = LoggerFactory.getLogger(UplinkFrameRouter.class);

    private final ProtocolAdapterRegistry registry;
    private final DeviceTenantResolver tenantResolver;
    private final MqttLogService logService;
    private final MqttDedupService dedupService;
    private final ApplicationEventPublisher eventPublisher;

    public UplinkFrameRouter(ProtocolAdapterRegistry registry,
                             DeviceTenantResolver tenantResolver,
                             MqttLogService logService,
                             MqttDedupService dedupService,
                             ApplicationEventPublisher eventPublisher) {
        this.registry = registry;
        this.tenantResolver = tenantResolver;
        this.logService = logService;
        this.dedupService = dedupService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param event 主题里的事件名，只用于旧去重白名单（{@code metric/order_event/cmd_ack}）；
     *              解码不看它，协议差异由 {@code adapter.decode} 吸收
     */
    public void route(String deviceNo, String event, RawFrame frame) {
        String payload = new String(frame.body(), StandardCharsets.UTF_8);
        logService.record("IN", frame.topicOrPeer(), deviceNo, payload);
        DeviceRouting routing = tenantResolver.route(deviceNo);
        if (routing == null) {
            dedupService.firstSeen(null, deviceNo, null, event, payload);
            log.warn("Dropping {} from unattributable device {}", event, deviceNo);
            return;
        }
        if (!dedupService.firstSeen(routing.tenantId(), deviceNo, routing.protocol(), event, payload)) {
            return;
        }
        DeviceProtocolAdapter adapter;
        try {
            adapter = registry.require(routing.protocol());
        } catch (IllegalStateException e) {
            log.warn("Dropping {} from {}: {}", event, deviceNo, e.getMessage());
            return;
        }
        List<PlatformEvent> decoded;
        try {
            decoded = adapter.decode(frame);
        } catch (Exception e) {
            // 解码失败只丢这一帧：回调线程抛出去会打断整个 MQTT 订阅。
            log.warn("Protocol {} failed to decode {} from {}: {}", adapter.protocol(), event, deviceNo,
                    e.getMessage());
            return;
        }
        for (PlatformEvent platformEvent : decoded) {
            if (platformEvent instanceof PlatformEvent.Unrecognized unrecognized) {
                // 平台侧 OTA 进度还在按原始帧收 ota_status；解不出语义的帧原样重投，不假装解出来了。
                eventPublisher.publishEvent(new UplinkMessageEvent(deviceNo, event, payload, routing.tenantId()));
                log.debug("Unrecognized {} from {}: {}", event, deviceNo, unrecognized.reason());
                continue;
            }
            eventPublisher.publishEvent(new UplinkEvent(routing.tenantId(), platformEvent));
        }
    }
}
