package com.balancloud.framework.cluster;

import com.balancloud.mqtt.MqttGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 实例存活登记：每 10 秒写一次 {@code balan:node:<instanceId>}，30 秒无心跳即视为下线。
 *
 * <p>只用于标准版集群监控展示，不参与任何选主；锁的归属仍以 Redis 里实际的 window/leader 键为准。
 * Redis 不可用时返回 available=false，而不是假装只有一个实例。
 */
@Service
public class ClusterMembershipService {

    private static final Logger log = LoggerFactory.getLogger(ClusterMembershipService.class);
    private static final String NODE_PREFIX = "balan:node:";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ClusterNode node;
    private final MqttGateway mqttGateway;
    private final Environment environment;
    private final TaskRunStatsRegistry taskStats;
    private final String edition;
    private final LocalDateTime startedAt = LocalDateTime.now();

    public ClusterMembershipService(StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper,
                                    ClusterNode node,
                                    MqttGateway mqttGateway,
                                    Environment environment,
                                    TaskRunStatsRegistry taskStats,
                                    @Value("${balan.edition:community}") String edition) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.node = node;
        this.mqttGateway = mqttGateway;
        this.environment = environment;
        this.taskStats = taskStats;
        this.edition = edition;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 5_000)
    public void heartbeat() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instanceId", node.id());
        payload.put("host", hostName());
        payload.put("pid", ProcessHandle.current().pid());
        payload.put("edition", edition);
        payload.put("profiles", List.of(environment.getActiveProfiles()));
        payload.put("startedAt", startedAt.format(TS));
        payload.put("heartbeatAt", LocalDateTime.now().format(TS));
        payload.put("mqttConnected", mqttGateway.isConnected());
        // 任务统计随心跳带出：标准版集群页读的是整份 payload，跨实例聚合不需要再加一条链路
        payload.put("tasks", taskStats.compact());
        try {
            redisTemplate.opsForValue().set(key(), objectMapper.writeValueAsString(payload), TTL);
        } catch (Exception e) {
            log.warn("Cluster heartbeat unavailable: {}", e.getMessage());
        }
    }

    public Map<String, Object> snapshot() {
        ensureSelfReported();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentInstanceId", node.id());
        result.put("heartbeatTtlSeconds", TTL.toSeconds());
        List<Map<String, Object>> instances;
        try {
            instances = readMembers();
            result.put("available", true);
            result.put("error", null);
        } catch (Exception e) {
            log.warn("Cluster membership unavailable: {}", e.getMessage());
            instances = new ArrayList<>();
            result.put("available", false);
            result.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        long online = instances.stream().filter(item -> Boolean.TRUE.equals(item.get("online"))).count();
        result.put("instanceCount", instances.size());
        result.put("onlineCount", online);
        result.put("instances", instances);
        return result;
    }

    @PreDestroy
    public void leave() {
        try {
            redisTemplate.delete(key());
        } catch (Exception e) {
            log.debug("Failed to remove cluster heartbeat: {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> readMembers() throws Exception {
        List<Map<String, Object>> instances = new ArrayList<>();
        for (String key : scan()) {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                continue;
            }
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<>() {
            });
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            Map<String, Object> item = new LinkedHashMap<>(payload);
            item.put("ttlSeconds", ttl);
            item.put("online", ttl != null && ttl > 0);
            item.put("self", node.isSelf(String.valueOf(payload.get("instanceId"))));
            instances.add(item);
        }
        instances.sort(Comparator
                .comparing((Map<String, Object> item) -> !Boolean.TRUE.equals(item.get("online")))
                .thenComparing(item -> !Boolean.TRUE.equals(item.get("self")))
                .thenComparing(item -> String.valueOf(item.get("instanceId"))));
        return instances;
    }

    private Set<String> scan() {
        Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> found = new LinkedHashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match(NODE_PREFIX + "*").count(200).build();
            collect(connection, options, found);
            return found;
        });
        return keys == null ? Set.of() : keys;
    }

    private void collect(RedisConnection connection, ScanOptions options, Set<String> found) {
        Cursor<byte[]> cursor = connection.keyCommands().scan(options);
        try {
            while (cursor.hasNext()) {
                found.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        } finally {
            close(cursor);
        }
    }

    private void close(Cursor<byte[]> cursor) {
        try {
            cursor.close();
        } catch (Exception ignored) {
            // 连接归还由 RedisTemplate 负责，关闭游标失败不影响本次盘点。
        }
    }

    private void ensureSelfReported() {
        try {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key()))) {
                heartbeat();
            }
        } catch (Exception e) {
            log.warn("Cluster heartbeat unavailable: {}", e.getMessage());
        }
    }

    private String key() {
        return NODE_PREFIX + node.id();
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
