package com.balancloud.mqtt;

import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.mqtt.config.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MQTT 报文消费幂等：共享订阅保证一条报文只投递给一个实例，这里再挡住 QoS1 重投
 * 与订阅组重分配时的重复投递，避免同一测点重复计费、重复回写端口状态。
 */
@Service
public class MqttDedupService {

    private static final Logger log = LoggerFactory.getLogger(MqttDedupService.class);
    private static final String PREFIX = "balan:mqtt:dup:";

    /**
     * online 不在其中：心跳报文内容固定但必须每轮刷新 last_online_at，
     * 去重会把在线设备误判成离线。
     * 下行 cmd 也不在其中：它普通订阅、每个实例都收得到，去重会让 Leader 实例那份被吞掉。
     */
    private static final Set<String> DEDUPED_EVENTS = Set.of("metric", "order_event", "cmd_ack");

    private final StringRedisTemplate redisTemplate;
    private final MqttProperties properties;
    private final ClusterNode node;
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();

    public MqttDedupService(StringRedisTemplate redisTemplate, MqttProperties properties, ClusterNode node) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.node = node;
    }

    /**
     * 去重键带租户段：{@code device_no} 从全局唯一变成租户内唯一，
     * 不加前缀时两个租户的同名设备会互吞对方的报文。解析不出租户的报文记 0 段。
     *
     * <p>协议段用来隔离「同一设备号换过协议」的窗口：不同方言的同一秒报文完全可能正文相同，
     * 混在一个键里会让新协议的帧被旧协议的指纹挡住。归属不明时记 UNKNOWN 段。
     */
    public boolean firstSeen(Long tenantId, String deviceNo, String protocol, String event, String payload) {
        received.incrementAndGet();
        if (!properties.isDedupeEnabled() || !DEDUPED_EVENTS.contains(event)) {
            return true;
        }
        String key = PREFIX + (tenantId == null ? 0L : tenantId) + ":" + deviceNo + ":" + event
                + ":" + normalizeProtocol(protocol) + ":" + fingerprint(payload);
        try {
            boolean fresh = Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(key, node.id(), Duration.ofSeconds(Math.max(1, properties.getDedupeTtlSeconds()))));
            if (!fresh) {
                skipped.incrementAndGet();
                log.debug("Duplicate {} message from {} skipped", event, deviceNo);
            }
            return fresh;
        } catch (Exception e) {
            // 与告警去重同一取舍：Redis 不可用时照常处理，计费侧还有测点时间戳兜底。
            log.warn("MQTT dedup unavailable for {}: {}", key, e.getMessage());
            return true;
        }
    }

    public long receivedCount() {
        return received.get();
    }

    public long skippedCount() {
        return skipped.get();
    }

    private static String normalizeProtocol(String protocol) {
        return protocol == null || protocol.isBlank() ? "UNKNOWN" : protocol.trim().toUpperCase();
    }

    private String fingerprint(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
