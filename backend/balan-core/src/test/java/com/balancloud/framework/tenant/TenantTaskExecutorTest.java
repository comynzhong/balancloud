package com.balancloud.framework.tenant;

import com.balancloud.framework.cluster.ClusterLockService;
import com.balancloud.framework.cluster.TaskRunStatsRegistry;
import com.balancloud.framework.tenant.mapper.TenantDirectoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantTaskExecutorTest {

    private static final Duration PERIOD = Duration.ofSeconds(5);
    private static final Duration WINDOW = Duration.ofSeconds(4);

    private TenantDirectoryMapper directoryMapper;
    private ClusterLockService clusterLock;
    private TaskRunStatsRegistry taskStats;
    private TenantTaskExecutor executor;

    @BeforeEach
    void setUp() {
        directoryMapper = mock(TenantDirectoryMapper.class);
        clusterLock = mock(ClusterLockService.class);
        taskStats = new TaskRunStatsRegistry();
        executor = new TenantTaskExecutor(new TenantRegistry(directoryMapper), clusterLock, taskStats);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void activeTenants(Long... tenantIds) {
        when(directoryMapper.selectActiveTenantIds()).thenReturn(List.of(tenantIds));
    }

    private Map<String, Object> row(String task) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) taskStats.snapshot().get("tasks");
        return tasks.stream().filter(item -> task.equals(item.get("task"))).findFirst().orElseThrow();
    }

    @Test
    void windowTaskRunsOncePerTenantInsideItsOwnContext() {
        activeTenants(1L, 2L);
        when(clusterLock.acquireWindow(anyString(), any(Duration.class))).thenReturn(true);
        List<Long> seen = new ArrayList<>();

        executor.forEach("command-retry", PERIOD, WINDOW, () -> seen.add(TenantContext.get()));

        assertThat(seen).containsExactly(1L, 2L);
        verify(clusterLock).acquireWindow("command-retry:1", WINDOW);
        verify(clusterLock).acquireWindow("command-retry:2", WINDOW);
    }

    /** 单租户部署保持拆分前的键名，避免一次没有收益的 Redis 键迁移。 */
    @Test
    void singleTenantKeepsTheLegacyLockKey() {
        activeTenants(1L);
        when(clusterLock.acquireWindow(anyString(), any(Duration.class))).thenReturn(true);

        executor.forEach("command-retry", PERIOD, WINDOW, () -> {
        });

        verify(clusterLock).acquireWindow("command-retry", WINDOW);
    }

    @Test
    void losingTheWindowSkipsOnlyThatTenant() {
        activeTenants(1L, 2L, 3L);
        when(clusterLock.acquireWindow(anyString(), any(Duration.class)))
                .thenAnswer(invocation -> !"command-retry:2".equals(invocation.getArgument(0)));
        List<Long> seen = new ArrayList<>();

        executor.forEach("command-retry", PERIOD, WINDOW, () -> seen.add(TenantContext.get()));

        assertThat(seen).containsExactly(1L, 3L);
    }

    @Test
    void oneFailingTenantDoesNotStarveTheOthers() {
        activeTenants(1L, 2L, 3L);
        when(clusterLock.acquireWindow(anyString(), any(Duration.class))).thenReturn(true);
        List<Long> seen = new ArrayList<>();

        executor.forEach("occupancy-sweep", PERIOD, WINDOW, () -> {
            Long tenantId = TenantContext.get();
            seen.add(tenantId);
            if (tenantId == 2L) {
                throw new IllegalStateException("脏数据");
            }
        });

        assertThat(seen).containsExactly(1L, 2L, 3L);
    }

    @Test
    void contextIsReleasedAfterTheRound() {
        activeTenants(7L);
        when(clusterLock.acquireWindow(anyString(), any(Duration.class))).thenReturn(true);

        executor.forEach("stop-sweep", PERIOD, WINDOW, () -> assertThat(TenantContext.get()).isEqualTo(7L));

        assertThat(TenantContext.get()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    void leaderTaskClaimsALeasePerTenant() {
        activeTenants(1L, 2L);
        when(clusterLock.isLeader(anyString(), any(Duration.class))).thenReturn(true);
        List<Long> seen = new ArrayList<>();

        executor.forEachLeader("simulator", PERIOD, Duration.ofSeconds(30),
                () -> seen.add(TenantContext.get()));

        assertThat(seen).containsExactly(1L, 2L);
        verify(clusterLock).isLeader("simulator:1", Duration.ofSeconds(30));
        verify(clusterLock).isLeader("simulator:2", Duration.ofSeconds(30));
    }

    /** 下行指令落在 MQTT 回调线程上，认领键必须与 forEachLeader 同一规则，否则两个实例都会应答。 */
    @Test
    void holdsLeaderUsesTheSameKeyAsTheLoop() {
        activeTenants(1L, 2L);
        when(clusterLock.isLeader(anyString(), any(Duration.class))).thenReturn(true);

        executor.forEachLeader("simulator", PERIOD, Duration.ofSeconds(30), () -> {
        });
        assertThat(executor.holdsLeader("simulator", 2L, Duration.ofSeconds(30))).isTrue();

        verify(clusterLock, times(2)).isLeader("simulator:2", Duration.ofSeconds(30));
    }

    @Test
    void startupHealingRunsEveryTenantWithoutALock() {
        activeTenants(1L, 2L);
        List<Long> seen = new ArrayList<>();

        executor.forEachOnce("simulator-init", () -> seen.add(TenantContext.get()));

        assertThat(seen).containsExactly(1L, 2L);
        verify(clusterLock, never()).acquireWindow(anyString(), any(Duration.class));
    }

    @Test
    void callAsSwitchesAndRestoresForPlatformReads() {
        assertThat(executor.callAs(9L, TenantContext::get)).isEqualTo(9L);
        assertThat(TenantContext.get()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    /** 校准是这批的意义所在：窗口/租约与周期的方向一旦写反，页面上必须看得见。 */
    @Test
    void windowNotShorterThanThePeriodIsFlaggedButStillRuns() {
        activeTenants(1L);
        when(clusterLock.acquireWindow(anyString(), any(Duration.class))).thenReturn(true);

        executor.forEach("ota-sweep", PERIOD, PERIOD, () -> {
        });

        Map<String, Object> row = row("ota-sweep");
        assertThat(row.get("warning")).isNotNull();
        assertThat(row.get("periodMillis")).isEqualTo(PERIOD.toMillis());
        assertThat(row.get("leaseMillis")).isEqualTo(PERIOD.toMillis());
        assertThat(row.get("runs")).isEqualTo(1L);
    }

    @Test
    void leaderLeaseNotLongerThanThePeriodIsFlagged() {
        activeTenants(1L);
        when(clusterLock.isLeader(anyString(), any(Duration.class))).thenReturn(true);

        executor.forEachLeader("simulator", PERIOD, WINDOW, () -> {
        });

        assertThat(row("simulator").get("warning")).isNotNull();
    }

    /** 认领到的实例与跳过的那份都按同一个任务键累计，跨实例并跑时两边读数正好相反。 */
    @Test
    void recordsRunsSkipsAndTheTenantCountPerTask() {
        activeTenants(1L, 2L, 3L);
        when(clusterLock.acquireWindow(anyString(), any(Duration.class)))
                .thenAnswer(invocation -> !"command-retry:2".equals(invocation.getArgument(0)));

        executor.forEach("command-retry", PERIOD, WINDOW, () -> {
        });

        Map<String, Object> row = row("command-retry");
        assertThat(row.get("mode")).isEqualTo(TaskRunStatsRegistry.MODE_WINDOW);
        assertThat(row.get("runs")).isEqualTo(2L);
        assertThat(row.get("skipped")).isEqualTo(1L);
        assertThat(row.get("lastTenantCount")).isEqualTo(3);
    }

    @Test
    void recordsTheErrorOfTheTenantThatThrew() {
        activeTenants(1L, 2L);
        when(clusterLock.acquireWindow(anyString(), any(Duration.class))).thenReturn(true);

        executor.forEach("stop-sweep", PERIOD, WINDOW, () -> {
            if (TenantContext.get() == 2L) {
                throw new IllegalStateException("TDengine 卡住");
            }
        });

        Map<String, Object> row = row("stop-sweep");
        assertThat(row.get("runs")).isEqualTo(2L);
        assertThat(row.get("errors")).isEqualTo(1L);
        assertThat(row.get("lastError")).isEqualTo("TDengine 卡住");
    }
}
