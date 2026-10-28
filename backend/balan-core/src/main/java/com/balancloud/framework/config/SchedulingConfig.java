package com.balancloud.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 调度线程池：主池承接全部 {@code @Scheduled}，晨报独占一条，避免 8:00 的推送排在秒级 tick 后面。
 *
 * <p>主池的 <b>Bean 名必须是 {@code taskScheduler}</b>：容器里出现多个 {@link TaskScheduler} 时，
 * {@code ScheduledAnnotationBeanPostProcessor} 只认这个名字（或 {@code @Primary}），
 * 否则它会退回自己新建的单线程执行器，{@code spring.task.scheduling.pool.size} 就成了改了也不生效的假开关。
 *
 * <p>容量仍由 Boot 的 builder 构建，为的是继续吃 {@code spring.task.scheduling.*} 全套属性
 * （池大小、线程名前缀、优雅停机）；3.3 里 {@code TaskSchedulerBuilder} 已标 {@code @Deprecated}，
 * 用的是它的替代者 {@link ThreadPoolTaskSchedulerBuilder}。
 */
@Configuration
public class SchedulingConfig {

    /** 与 {@code @Scheduled(scheduler = ...)} 里写的字符串一致。 */
    public static final String REPORT_SCHEDULER = "reportTaskScheduler";

    @Bean(name = "taskScheduler")
    @Primary
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    /**
     * 晨报专用池。它是自己 new 出来的，不吃 {@code spring.task.scheduling.*}，
     * 所以容量单独给一个配置项；优雅停机等行为沿用 Boot 主池的同一组默认值。
     */
    @Bean(name = REPORT_SCHEDULER)
    public ThreadPoolTaskScheduler reportTaskScheduler(
            @Value("${balan.scheduling.report-pool-size:1}") int poolSize,
            @Value("${balan.scheduling.report-await-termination-seconds:10}") int awaitTerminationSeconds) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("balan-report-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(awaitTerminationSeconds);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
