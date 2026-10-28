package com.balancloud.framework.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 定时任务的进程内运行统计：每个任务键累计「执行了几次、被窗口挡掉几次、上轮与最坏耗时、异常」。
 *
 * <p>刻意不落库也不跨实例合并——重启归零，只反映本进程。跨实例的读法是把 {@link #compact()}
 * 塞进实例心跳 payload，由标准版集群监控逐实例展示。
 */
@Component
public class TaskRunStatsRegistry {

    private static final Logger log = LoggerFactory.getLogger(TaskRunStatsRegistry.class);

    public static final String MODE_WINDOW = "window";
    public static final String MODE_LEADER = "leader";
    public static final String MODE_PLAIN = "plain";

    private final Map<String, TaskStat> stats = new ConcurrentHashMap<>();
    /** 同一条组合警告只在第一次声明时打印，避免每秒任务刷屏。 */
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    /**
     * 声明任务与其周期。窗口必须严格小于周期（否则一个周期内可能跑两遍），
     * leader 租约必须大于周期（否则会在两轮之间过期，备用实例接手后两边同时上报）。
     * 违反时只记一条 WARN，不抛异常：调度线程里的异常会让这个任务从此不再执行。
     */
    public void declare(String task, String mode, Duration period, Duration lease) {
        TaskStat stat = stats.computeIfAbsent(task, TaskStat::new);
        stat.describe(mode, period, lease);
        String warning = stat.warning;
        if (warning != null && warned.add(task + "|" + warning)) {
            log.warn("Scheduled task {}: {}", task, warning);
        }
    }

    public void recordRun(String task, LocalDateTime startAt, long durationMillis, String error) {
        TaskStat stat = stats.get(task);
        if (stat != null) {
            stat.recordRun(startAt, durationMillis, error);
        }
    }

    public void recordSkip(String task) {
        TaskStat stat = stats.get(task);
        if (stat != null) {
            stat.skipped.increment();
        }
    }

    /** 本轮认领到的租户数，随每轮刷新，用于判断「这个任务在当前部署里到底覆盖几个租户」。 */
    public void recordTenantCount(String task, int tenantCount) {
        TaskStat stat = stats.get(task);
        if (stat != null) {
            stat.lastTenantCount = tenantCount;
        }
    }

    public String warning(String task) {
        TaskStat stat = stats.get(task);
        return stat == null ? null : stat.warning;
    }

    /** 完整视图，按任务名排序；周期与租约以毫秒给出，便于和 {@code @Scheduled} 的值对账。 */
    public Map<String, Object> snapshot() {
        List<TaskStat> sorted = sorted();
        List<Map<String, Object>> rows = new ArrayList<>(sorted.size());
        for (TaskStat stat : sorted) {
            rows.add(stat.view());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskCount", rows.size());
        result.put("tasks", rows);
        return result;
    }

    /**
     * 心跳 payload 用的紧凑摘要：除了「在不在跑、有没有被挡」，还带上周期/租约与校准警告，
     * 因为双实例并跑时只有标准版页面能同时看到两份读数，方向写反要在这一眼看出来。
     */
    public List<Map<String, Object>> compact() {
        List<TaskStat> sorted = sorted();
        List<Map<String, Object>> rows = new ArrayList<>(sorted.size());
        for (TaskStat stat : sorted) {
            rows.add(stat.compactView());
        }
        return rows;
    }

    private List<TaskStat> sorted() {
        List<TaskStat> sorted = new ArrayList<>(stats.values());
        sorted.sort(Comparator.comparing(stat -> stat.task));
        return sorted;
    }

    static final class TaskStat {
        private final String task;
        private final LongAdder runs = new LongAdder();
        private final LongAdder skipped = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalMillis = new LongAdder();
        private volatile String mode = MODE_PLAIN;
        private volatile long periodMillis;
        private volatile long leaseMillis;
        private volatile String warning;
        private volatile LocalDateTime lastStartAt;
        private volatile long lastDurationMs;
        private volatile long worstDurationMs;
        private volatile int lastTenantCount;
        private volatile String lastError;

        TaskStat(String task) {
            this.task = task;
        }

        void describe(String mode, Duration period, Duration lease) {
            this.mode = mode;
            this.periodMillis = period == null ? 0 : period.toMillis();
            this.leaseMillis = lease == null ? 0 : lease.toMillis();
            this.warning = validate(mode, periodMillis, leaseMillis);
        }

        void recordRun(LocalDateTime startAt, long durationMillis, String error) {
            runs.increment();
            totalMillis.add(durationMillis);
            lastStartAt = startAt;
            lastDurationMs = durationMillis;
            worstDurationMs = Math.max(worstDurationMs, durationMillis);
            if (error == null) {
                lastError = null;
            } else {
                errors.increment();
                lastError = error;
            }
        }

        Map<String, Object> view() {
            long executed = runs.sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("task", task);
            row.put("mode", mode);
            row.put("periodMillis", periodMillis);
            row.put("leaseMillis", leaseMillis);
            row.put("warning", warning);
            row.put("runs", executed);
            row.put("skipped", skipped.sum());
            row.put("errors", errors.sum());
            row.put("lastStartAt", lastStartAt == null ? null : lastStartAt.toString());
            row.put("lastDurationMs", lastDurationMs);
            row.put("avgDurationMs", executed == 0 ? 0 : totalMillis.sum() / executed);
            row.put("worstDurationMs", worstDurationMs);
            row.put("lastTenantCount", lastTenantCount);
            row.put("lastError", lastError);
            return row;
        }

        Map<String, Object> compactView() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("task", task);
            row.put("mode", mode);
            row.put("periodMillis", periodMillis);
            row.put("leaseMillis", leaseMillis);
            row.put("warning", warning);
            row.put("runs", runs.sum());
            row.put("skipped", skipped.sum());
            row.put("errors", errors.sum());
            row.put("lastMs", lastDurationMs);
            row.put("worstMs", worstDurationMs);
            return row;
        }

        /** 周期与租约的方向检查；返回 null 表示没有需要提示的组合。 */
        private static String validate(String mode, long periodMillis, long leaseMillis) {
            if (periodMillis <= 0 || leaseMillis <= 0) {
                return null;
            }
            if (MODE_WINDOW.equals(mode) && leaseMillis >= periodMillis) {
                return "窗口不短于周期（" + leaseMillis + "ms ≥ " + periodMillis + "ms）：同一周期内可能被两个实例各跑一遍";
            }
            if (MODE_LEADER.equals(mode) && leaseMillis <= periodMillis) {
                return "租约不长于周期（" + leaseMillis + "ms ≤ " + periodMillis + "ms）：租约会在相邻两轮之间过期";
            }
            return null;
        }
    }
}
