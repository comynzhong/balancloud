package com.balancloud.framework.cluster;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRunStatsRegistryTest {

    private static final Duration PERIOD = Duration.ofSeconds(20);

    private TaskRunStatsRegistry registry;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        registry = new TaskRunStatsRegistry();
        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(TaskRunStatsRegistry.class)).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(TaskRunStatsRegistry.class)).detachAppender(appender);
    }

    private Map<String, Object> row(String task) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) registry.snapshot().get("tasks");
        return tasks.stream().filter(item -> task.equals(item.get("task"))).findFirst().orElseThrow();
    }

    @Test
    void countsRunsAndSkipsSeparately() {
        registry.declare("ota-sweep", TaskRunStatsRegistry.MODE_WINDOW, PERIOD, Duration.ofSeconds(19));
        registry.recordRun("ota-sweep", LocalDateTime.now(), 12, null);
        registry.recordSkip("ota-sweep");
        registry.recordSkip("ota-sweep");

        Map<String, Object> row = row("ota-sweep");
        assertThat(row.get("runs")).isEqualTo(1L);
        assertThat(row.get("skipped")).isEqualTo(2L);
        assertThat(row.get("errors")).isEqualTo(0L);
    }

    @Test
    void keepsTheWorstDurationAndAveragesTheRest() {
        registry.declare("stop-sweep", TaskRunStatsRegistry.MODE_WINDOW, PERIOD, Duration.ofSeconds(4));
        registry.recordRun("stop-sweep", LocalDateTime.now(), 30, null);
        registry.recordRun("stop-sweep", LocalDateTime.now(), 90, null);
        registry.recordRun("stop-sweep", LocalDateTime.now(), 60, null);

        Map<String, Object> row = row("stop-sweep");
        assertThat(row.get("worstDurationMs")).isEqualTo(90L);
        assertThat(row.get("lastDurationMs")).isEqualTo(60L);
        assertThat(row.get("avgDurationMs")).isEqualTo(60L);
    }

    @Test
    void countsErrorsAndClearsThemOnTheNextGoodRun() {
        registry.declare("offline-settle", TaskRunStatsRegistry.MODE_WINDOW, PERIOD, Duration.ofSeconds(50));
        registry.recordRun("offline-settle", LocalDateTime.now(), 5, "TDengine 超时");
        assertThat(row("offline-settle").get("lastError")).isEqualTo("TDengine 超时");

        registry.recordRun("offline-settle", LocalDateTime.now(), 5, null);

        Map<String, Object> row = row("offline-settle");
        assertThat(row.get("errors")).isEqualTo(1L);
        assertThat(row.get("runs")).isEqualTo(2L);
        assertThat(row.get("lastError")).isNull();
    }

    /** 校准方向：窗口必须严格短于周期，两个值相等即退化。 */
    @Test
    void flagsAWindowThatIsNotShorterThanThePeriod() {
        registry.declare("ota-sweep", TaskRunStatsRegistry.MODE_WINDOW, PERIOD, PERIOD);

        assertThat(row("ota-sweep").get("warning")).isInstanceOf(String.class);
    }

    /** 租约方向相反：必须严格长于周期，否则会在相邻两轮之间过期。 */
    @Test
    void flagsALeaseThatIsNotLongerThanThePeriod() {
        registry.declare("simulator", TaskRunStatsRegistry.MODE_LEADER, Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(row("simulator").get("warning")).isInstanceOf(String.class);
    }

    @Test
    void acceptsTheTwoCorrectDirections() {
        registry.declare("command-retry", TaskRunStatsRegistry.MODE_WINDOW, Duration.ofSeconds(2), Duration.ofMillis(1500));
        registry.declare("simulator", TaskRunStatsRegistry.MODE_LEADER, Duration.ofSeconds(1), Duration.ofSeconds(30));

        assertThat(row("command-retry").get("warning")).isNull();
        assertThat(row("simulator").get("warning")).isNull();
    }

    /** 不加锁的每实例任务没有周期可核对，不能误报成组合错误。 */
    @Test
    void plainTasksDeclareNoCombination() {
        registry.declare("simulator-init", TaskRunStatsRegistry.MODE_PLAIN, null, null);

        assertThat(row("simulator-init").get("warning")).isNull();
        assertThat(row("simulator-init").get("mode")).isEqualTo(TaskRunStatsRegistry.MODE_PLAIN);
    }

    /** 1 秒级的 tick 每轮都会重新声明，同一条组合警告只许打印一次。 */
    @Test
    void warnsOncePerTaskInsteadOfEveryCycle() {
        for (int round = 0; round < 50; round++) {
            registry.declare("power-sample", TaskRunStatsRegistry.MODE_WINDOW,
                    Duration.ofSeconds(10), Duration.ofSeconds(10));
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("power-sample");
    }

    @Test
    void heartbeatSummaryKeepsOnlyWhatCrossInstanceReadingNeeds() {
        registry.declare("command-retry", TaskRunStatsRegistry.MODE_WINDOW, Duration.ofSeconds(2), Duration.ofMillis(1500));
        registry.recordRun("command-retry", LocalDateTime.now(), 7, null);

        Map<String, Object> compact = registry.compact().get(0);
        assertThat(compact).containsOnlyKeys("task", "mode", "periodMillis", "leaseMillis", "warning",
                "runs", "skipped", "errors", "lastMs", "worstMs");
        assertThat(compact.get("runs")).isEqualTo(1L);
        assertThat(compact.get("periodMillis")).isEqualTo(2000L);
        assertThat(compact.get("leaseMillis")).isEqualTo(1500L);
        assertThat(compact.get("warning")).isNull();
    }

    @Test
    void snapshotIsSortedAndCarriesTheTaskCount() {
        registry.declare("stop-sweep", TaskRunStatsRegistry.MODE_WINDOW, PERIOD, Duration.ofSeconds(4));
        registry.declare("command-retry", TaskRunStatsRegistry.MODE_WINDOW, PERIOD, Duration.ofSeconds(4));

        assertThat(registry.snapshot().get("taskCount")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) registry.snapshot().get("tasks");
        assertThat(tasks.get(0).get("task")).isEqualTo("command-retry");
    }

    @Test
    void recordsForAnUndeclaredTaskDoNotBlowUp() {
        registry.recordSkip("ghost");
        registry.recordRun("ghost", LocalDateTime.now(), 1, null);

        assertThat(registry.snapshot().get("taskCount")).isEqualTo(0);
    }
}
