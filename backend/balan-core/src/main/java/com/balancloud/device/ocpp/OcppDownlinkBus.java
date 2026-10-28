package com.balancloud.device.ocpp;

import com.balancloud.device.protocol.RawFrame;
import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.framework.cluster.ClusterProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * OCPP 下行投递：会话在本实例就直接写；不在就广播到其他实例。
 *
 * <p>只用一个 Redis 频道而不是按 identity 的模式订阅：现有监听容器都是 {@code ChannelTopic}，
 * 单频道 + 内存过滤改动最小；单个部署收到全部 OCPP 下行的体量按在线桩数量评估可接受（§48.3）。
 *
 * <p>Redis 不可用时明确返回失败，让指令重试；不做「先记成功再广播」的假投递。
 */
@Service
public class OcppDownlinkBus {

    private static final Logger log = LoggerFactory.getLogger(OcppDownlinkBus.class);
    private static final String HEADER_UNIQUE_ID = "ocpp.uniqueId";
    private static final String HEADER_CMD_ID = "ocpp.cmdId";
    private static final String HEADER_TYPE = "ocpp.type";
    private static final String HEADER_PORT_NO = "ocpp.portNo";

    private final OcppSessionRegistry registry;
    private final OcppPendingStore pendingStore;
    private final StringRedisTemplate redisTemplate;
    private final ClusterProperties clusterProperties;
    private final ClusterNode node;
    private final ObjectMapper objectMapper;

    public OcppDownlinkBus(OcppSessionRegistry registry,
                           OcppPendingStore pendingStore,
                           StringRedisTemplate redisTemplate,
                           ClusterProperties clusterProperties,
                           ClusterNode node,
                           ObjectMapper objectMapper) {
        this.registry = registry;
        this.pendingStore = pendingStore;
        this.redisTemplate = redisTemplate;
        this.clusterProperties = clusterProperties;
        this.node = node;
        this.objectMapper = objectMapper;
    }

    public boolean send(RawFrame frame) {
        String identity = frame.topicOrPeer();
        String uniqueId = frame.headers().get(HEADER_UNIQUE_ID);
        String cmdId = frame.headers().get(HEADER_CMD_ID);
        if (StringUtils.hasText(uniqueId) && StringUtils.hasText(cmdId)) {
            pendingStore.register(identity, uniqueId, new OcppPendingStore.PendingCommand(
                    cmdId, frame.headers().get(HEADER_TYPE), parseInt(frame.headers().get(HEADER_PORT_NO))));
        }
        String text = frame.bodyAsText();
        if (registry.send(identity, text)) {
            return true;
        }
        try {
            redisTemplate.convertAndSend(clusterProperties.getOcppDownChannel(),
                    objectMapper.writeValueAsString(new Envelope(node.id(), identity, text)));
            log.debug("OCPP downlink for {} forwarded to another instance", identity);
            return true;
        } catch (Exception e) {
            log.warn("OCPP downlink for {} failed: no local session and Redis unavailable ({})",
                    identity, e.getMessage());
            return false;
        }
    }

    /** 订阅端：只有持有该 identity 会话的实例会真正发送。 */
    public void onRemoteMessage(String json) {
        try {
            Envelope envelope = objectMapper.readValue(json, Envelope.class);
            if (node.id().equals(envelope.instanceId())) {
                return;
            }
            if (!registry.send(envelope.identity(), envelope.body())) {
                log.debug("OCPP frame for {} arrived but no local session holds it", envelope.identity());
            }
        } catch (Exception e) {
            log.warn("Invalid OCPP downlink envelope: {}", e.getMessage());
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record Envelope(String instanceId, String identity, String body) {
    }
}
