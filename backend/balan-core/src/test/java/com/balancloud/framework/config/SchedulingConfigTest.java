package com.balancloud.framework.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-1 的核心风险不是「池子多大」，而是「{@code @Scheduled} 到底跑在哪个池」：
 * 容器里有多个 {@link TaskScheduler} 时 {@code ScheduledAnnotationBeanPostProcessor} 只认
 * Bean 名 {@code taskScheduler}，认错就退回它自建的单线程执行器，属性全部白改。
 * 所以这里量的是实际执行线程的名字，不是配置项的回读。
 */
class SchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TaskSchedulingAutoConfiguration.class,
                    SchedulingConfig.class, SchedulingProbe.class);

    @Test
    void scheduledMethodsRunOnTheConfiguredMainPool() {
        runner.withPropertyValues("spring.task.scheduling.pool.size=4",
                        "spring.task.scheduling.thread-name-prefix=balan-sched-")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ThreadPoolTaskScheduler main =
                            (ThreadPoolTaskScheduler) context.getBean("taskScheduler", TaskScheduler.class);
                    assertThat(main.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);

                    SchedulingProbe probe = context.getBean(SchedulingProbe.class);
                    probe.await();
                    assertThat(probe.mainThread.get()).startsWith("balan-sched-");
                    assertThat(probe.reportThread.get()).startsWith("balan-report-");
                });
    }

    /** Boot 的自动配置必须整体退让：否则要么同名 Bean 冲突启动失败，要么冒出第三个调度池。 */
    @Test
    void theProjectOwnsBothSchedulersAndBootAddsNoThird() {
        runner.withPropertyValues("spring.task.scheduling.pool.size=4")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(TaskScheduler.class).values())
                            .hasSize(2)
                            .allMatch(scheduler -> scheduler instanceof ThreadPoolTaskScheduler);
                });
    }

    @Test
    void reportPoolHasItsOwnCapacityAndPrefix() {
        runner.withPropertyValues("balan.scheduling.report-pool-size=2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ThreadPoolTaskScheduler report = (ThreadPoolTaskScheduler)
                            context.getBean(SchedulingConfig.REPORT_SCHEDULER, TaskScheduler.class);
                    assertThat(report.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
                    assertThat(report.getThreadNamePrefix()).isEqualTo("balan-report-");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingProbe {

        private final AtomicReference<String> mainThread = new AtomicReference<>();
        private final AtomicReference<String> reportThread = new AtomicReference<>();

        @Scheduled(fixedDelay = 50, initialDelay = 20)
        public void onMainPool() {
            mainThread.compareAndSet(null, Thread.currentThread().getName());
        }

        @Scheduled(scheduler = SchedulingConfig.REPORT_SCHEDULER, fixedDelay = 50, initialDelay = 20)
        public void onReportPool() {
            reportThread.compareAndSet(null, Thread.currentThread().getName());
        }

        void await() {
            long deadline = System.currentTimeMillis() + 5000;
            while ((mainThread.get() == null || reportThread.get() == null)
                    && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
