package com.balancloud.framework.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 定时任务的多实例协调。两类语义：
 * 「窗口」用于一轮只需要一个实例跑一次的扫描，窗口内其余实例直接跳过；
 * 「leader」用于有内存状态、必须固定在同一实例的循环（内置仿真器）。
 * 两种都遵循与告警去重相同的取舍：Redis 不可用时照常执行，宁可重复也不静默停摆。
 *
 * <p>窗口锁只保证「同一 TTL 内只有一个实例进去」，<b>不是 exactly-once</b>：任务体比 TTL 慢时
 * 另一个实例照样能进下一轮，所以写路径必须自带行级 CAS 或唯一键兜底。
 */
@Service
public class ClusterLockService {

    private static final Logger log = LoggerFactory.getLogger(ClusterLockService.class);
    private static final String WINDOW_PREFIX = "balan:window:";
    private static final String LEADER_PREFIX = "balan:leader:";

    /**
     * 抢 leader 与续约必须一次往返完成：先 {@code SET NX}，抢不到时只有在「键里还是我」
     * 的前提下续期。分两步（先 GET 判断、再 EXPIRE）会留出「判断时是我、续期时已过期并被别人抢走」
     * 的窗口，于是两个实例同时自认 leader。也不能改成「GET 为空就无条件 SET」——
     * 那样两个实例同时看到空键会各自写入并双双返回成功。
     */
    private static final RedisScript<Long> LEADER_ACQUIRE = new DefaultRedisScript<>("""
            if redis.call('set', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then
              return 1
            end
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ClusterNode node;

    public ClusterLockService(StringRedisTemplate redisTemplate, ClusterNode node) {
        this.redisTemplate = redisTemplate;
        this.node = node;
    }

    public boolean acquireWindow(String task, Duration window) {
        String key = WINDOW_PREFIX + task;
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, node.id(), window));
        } catch (Exception e) {
            log.warn("Cluster window {} unavailable: {}", task, e.getMessage());
            return true;
        }
    }

    public boolean isLeader(String task, Duration ttl) {
        String key = LEADER_PREFIX + task;
        try {
            Long owned = redisTemplate.execute(LEADER_ACQUIRE, List.of(key),
                    node.id(), String.valueOf(ttl.toMillis()));
            return owned != null && owned > 0;
        } catch (Exception e) {
            log.warn("Cluster leader {} unavailable: {}", task, e.getMessage());
            return true;
        }
    }
}
