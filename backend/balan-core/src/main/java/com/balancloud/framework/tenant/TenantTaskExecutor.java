package com.balancloud.framework.tenant;

import com.balancloud.framework.cluster.ClusterLockService;
import com.balancloud.framework.cluster.TaskRunStatsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务的租户循环：没有 HTTP 会话的线程原本只会落到默认租户，多租户部署下其余租户就没人扫。
 *
 * <p>窗口锁与 Sticky Leader 都按租户各一把，单个租户抛异常只记 WARN 并继续下一个，
 * 否则一个租户的脏数据会拖死全部租户的清扫。
 *
 * <p>每次调用都声明「周期」与「窗口/租约」两个值：周期由调用点从 {@code @Scheduled} 的常量派生，
 * 执行器据此校验组合（见 {@link TaskRunStatsRegistry#declare}）并记录每轮耗时与跳过次数。
 */
@Component
public class TenantTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TenantTaskExecutor.class);

    private final TenantRegistry tenantRegistry;
    private final ClusterLockService clusterLock;
    private final TaskRunStatsRegistry taskStats;

    public TenantTaskExecutor(TenantRegistry tenantRegistry, ClusterLockService clusterLock,
                              TaskRunStatsRegistry taskStats) {
        this.tenantRegistry = tenantRegistry;
        this.clusterLock = clusterLock;
        this.taskStats = taskStats;
    }

    /** 窗口锁型任务：每个租户在自己的上下文与自己的锁里跑一次。 */
    public void forEach(String task, Duration period, Duration window, Runnable body) {
        taskStats.declare(task, TaskRunStatsRegistry.MODE_WINDOW, period, window);
        List<Long> tenants = tenantRegistry.activeTenantIds();
        taskStats.recordTenantCount(task, tenants.size());
        for (Long tenantId : tenants) {
            if (!clusterLock.acquireWindow(lockKey(task, tenantId, tenants.size()), window)) {
                taskStats.recordSkip(task);
                continue;
            }
            run(task, tenantId, body);
        }
    }

    /** 独占型任务（仿真器上报）：每个租户认自己的 leader。 */
    public void forEachLeader(String task, Duration period, Duration ttl, Runnable body) {
        taskStats.declare(task, TaskRunStatsRegistry.MODE_LEADER, period, ttl);
        List<Long> tenants = tenantRegistry.activeTenantIds();
        taskStats.recordTenantCount(task, tenants.size());
        for (Long tenantId : tenants) {
            if (!clusterLock.isLeader(lockKey(task, tenantId, tenants.size()), ttl)) {
                taskStats.recordSkip(task);
                continue;
            }
            run(task, tenantId, body);
        }
    }

    /** 不加锁的按租户循环，用于启动自愈这类必须每实例都跑一遍的场景。 */
    public void forEachOnce(String task, Runnable body) {
        taskStats.declare(task, TaskRunStatsRegistry.MODE_PLAIN, null, null);
        List<Long> tenants = tenantRegistry.activeTenantIds();
        taskStats.recordTenantCount(task, tenants.size());
        for (Long tenantId : tenants) {
            run(task, tenantId, body);
        }
    }

    /** 循环之外的认领（仿真器下行落在 MQTT 回调线程上），必须与 {@link #forEachLeader} 同一键名规则。 */
    public boolean holdsLeader(String task, Long tenantId, Duration ttl) {
        return clusterLock.isLeader(lockKey(task, tenantId, tenantRegistry.activeCount()), ttl);
    }

    /** 当前会话/上下文所属租户，供事件回调线程显式切换。 */
    public List<Long> activeTenantIds() {
        return tenantRegistry.activeTenantIds();
    }

    /** 平台侧跨租户读数据用的显式切换：调用方负责在同一线程里用完即走。 */
    public <T> T callAs(Long tenantId, java.util.function.Supplier<T> body) {
        TenantContext.use(tenantId);
        try {
            return body.get();
        } finally {
            TenantContext.clear();
        }
    }

    private void run(String task, Long tenantId, Runnable body) {
        LocalDateTime startAt = LocalDateTime.now();
        long started = System.nanoTime();
        TenantContext.use(tenantId);
        String error = null;
        try {
            body.run();
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("Scheduled task {} failed for tenant {}: {}", task, tenantId, e.getMessage(), e);
        } finally {
            TenantContext.clear();
            taskStats.recordRun(task, startAt, (System.nanoTime() - started) / 1_000_000, error);
        }
    }

    /**
     * 单租户部署保持既有键名（{@code balan:window:command-retry}），避免一次无意义的缓存键迁移；
     * 两个及以上租户才拆成 {@code task:tenantId}，否则一个租户抢到窗口会把别的租户一起挡掉。
     */
    private String lockKey(String task, Long tenantId, int tenantCount) {
        return tenantCount <= 1 ? task : task + ":" + tenantId;
    }
}
