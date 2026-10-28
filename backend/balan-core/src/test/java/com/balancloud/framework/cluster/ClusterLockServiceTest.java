package com.balancloud.framework.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClusterLockServiceTest {

    private static final List<String> LEADER_KEY = List.of("balan:leader:simulator");

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ClusterLockService lockService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ClusterProperties properties = new ClusterProperties();
        properties.setInstanceId("node-a");
        lockService = new ClusterLockService(redisTemplate, new ClusterNode(properties));
    }

    @Test
    void windowRunsOnTheInstanceThatReservedIt() {
        when(valueOperations.setIfAbsent(eq("balan:window:power-sample"), eq("node-a"), any(Duration.class)))
                .thenReturn(true);
        assertThat(lockService.acquireWindow("power-sample", Duration.ofSeconds(9))).isTrue();

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        assertThat(lockService.acquireWindow("power-sample", Duration.ofSeconds(9))).isFalse();
    }

    /**
     * 抢与续都在同一段脚本里完成：一次往返、两个参数（实例号与 TTL 毫秒）。
     * 契约层面钉住「不再有 GET + EXPIRE 两步」——原子性本身是 Redis 侧行为，
     * 由双实例并跑的验收脚本（§54.9 门禁 2）对真实 Redis 证明。
     */
    @Test
    @SuppressWarnings("unchecked")
    void leaderRenewsItsOwnLeaseInASingleRoundTrip() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

        assertThat(lockService.isLeader("simulator", Duration.ofSeconds(5))).isTrue();

        verify(redisTemplate).execute(any(RedisScript.class), eq(LEADER_KEY), eq("node-a"), eq("5000"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void followerYieldsToTheCurrentLeader() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);

        assertThat(lockService.isLeader("simulator", Duration.ofSeconds(5))).isFalse();
    }

    /** 脚本回退或键被删导致返回 nil 时按「不是 leader」处理，不能当成异常路径放行。 */
    @Test
    @SuppressWarnings("unchecked")
    void nullScriptResultIsNotLeader() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(null);

        assertThat(lockService.isLeader("simulator", Duration.ofSeconds(5))).isFalse();
    }

    @Test
    void redisOutageKeepsTasksRunning() {
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));
        when(redisTemplate.execute(any(), anyList(), any(), any()))
                .thenThrow(new IllegalStateException("redis down"));

        assertThat(lockService.acquireWindow("stop-sweep", Duration.ofSeconds(4))).isTrue();
        assertThat(lockService.isLeader("simulator", Duration.ofSeconds(5))).isTrue();
    }
}
