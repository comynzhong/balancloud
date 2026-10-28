package com.balancloud.framework.cluster;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读盘点定时任务的锁归属。用 SCAN 而不是 KEYS，避免节点多、窗口键多时阻塞 Redis。
 */
@Service
public class ClusterLockInspectorService {

    private static final String WINDOW_PREFIX = "balan:window:";
    private static final String LEADER_PREFIX = "balan:leader:";

    private final StringRedisTemplate redisTemplate;

    public ClusterLockInspectorService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<Map<String, Object>> inspect() {
        List<Map<String, Object>> locks = redisTemplate.execute(
                (RedisCallback<List<Map<String, Object>>>) connection -> {
                    List<Map<String, Object>> result = new ArrayList<>();
                    collect(connection, WINDOW_PREFIX, "window", result);
                    collect(connection, LEADER_PREFIX, "leader", result);
                    return result;
                });
        if (locks == null) {
            return List.of();
        }
        locks.sort(Comparator
                .comparing((Map<String, Object> item) -> String.valueOf(item.get("scope")))
                .thenComparing(item -> String.valueOf(item.get("task")))
                .thenComparingLong(item -> item.get("tenantId") instanceof Number number
                        ? number.longValue() : 0L));
        return locks;
    }

    private void collect(RedisConnection connection, String prefix, String scope, List<Map<String, Object>> result) {
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(500).build();
        Cursor<byte[]> cursor = connection.keyCommands().scan(options);
        try {
            while (cursor.hasNext()) {
                byte[] rawKey = cursor.next();
                String key = new String(rawKey, StandardCharsets.UTF_8);
                byte[] rawValue = connection.stringCommands().get(rawKey);
                Long ttl = connection.keyCommands().ttl(rawKey);
                Map<String, Object> item = parse(prefix, scope, key);
                item.put("owner", rawValue == null ? null : new String(rawValue, StandardCharsets.UTF_8));
                item.put("ttlSeconds", ttl);
                item.put("expired", ttl == null || ttl < 0);
                result.add(item);
            }
        } finally {
            try {
                cursor.close();
            } catch (Exception ignored) {
                // 关闭游标失败不影响本次盘点，连接由 RedisTemplate 回收。
            }
        }
    }

    /**
     * 键名拆任务与租户：{@code TenantTaskExecutor} 只在 ≥2 个活跃租户时追加租户段，
     * 所以纯数字后缀才当租户号，否则整段是任务名（例如 {@code morning-report-push}）。
     */
    static Map<String, Object> parse(String prefix, String scope, String key) {
        String suffix = key.substring(prefix.length());
        Long tenantId = null;
        String task = suffix;
        int separator = suffix.lastIndexOf(':');
        if (separator > 0 && separator < suffix.length() - 1) {
            String tail = suffix.substring(separator + 1);
            if (tail.chars().allMatch(Character::isDigit)) {
                tenantId = Long.valueOf(tail);
                task = suffix.substring(0, separator);
            }
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("scope", scope);
        item.put("task", task);
        item.put("tenantId", tenantId);
        return item;
    }
}
