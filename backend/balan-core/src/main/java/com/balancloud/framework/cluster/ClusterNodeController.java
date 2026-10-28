package com.balancloud.framework.cluster;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.framework.config.SchedulingConfig;
import com.balancloud.framework.spi.SpiSnapshotService;
import com.balancloud.mqtt.MqttDedupService;
import com.balancloud.mqtt.MqttGateway;
import com.balancloud.mqtt.config.MqttProperties;
import com.balancloud.ws.EventStreamService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本节点实例视图：多实例部署时用来确认「这个请求落在哪个进程、它的 MQTT 身份与广播通道」，
 * 也是排查重复消费与事件丢失的第一个入口。
 */
@RestController
@RequestMapping("/api/v1/cluster")
public class ClusterNodeController {

    private final ClusterNode node;
    private final ClusterProperties clusterProperties;
    private final MqttGateway mqttGateway;
    private final MqttProperties mqttProperties;
    private final MqttDedupService dedupService;
    private final EventStreamService eventStreamService;
    private final SpiSnapshotService spiSnapshotService;
    private final TaskRunStatsRegistry taskStats;
    private final TaskScheduler taskScheduler;
    private final TaskScheduler reportTaskScheduler;

    public ClusterNodeController(ClusterNode node,
                                 ClusterProperties clusterProperties,
                                 MqttGateway mqttGateway,
                                 MqttProperties mqttProperties,
                                 MqttDedupService dedupService,
                                 EventStreamService eventStreamService,
                                 SpiSnapshotService spiSnapshotService,
                                 TaskRunStatsRegistry taskStats,
                                 @Qualifier("taskScheduler") TaskScheduler taskScheduler,
                                 @Qualifier(SchedulingConfig.REPORT_SCHEDULER) TaskScheduler reportTaskScheduler) {
        this.node = node;
        this.clusterProperties = clusterProperties;
        this.mqttGateway = mqttGateway;
        this.mqttProperties = mqttProperties;
        this.dedupService = dedupService;
        this.eventStreamService = eventStreamService;
        this.spiSnapshotService = spiSnapshotService;
        this.taskStats = taskStats;
        this.taskScheduler = taskScheduler;
        this.reportTaskScheduler = reportTaskScheduler;
    }

    @PreAuthorize("@ss.hasPermi('simulator:list')")
    @GetMapping("/node")
    public AjaxResult node() {
        Map<String, Object> mqtt = new LinkedHashMap<>();
        mqtt.put("enabled", mqttProperties.isEnabled());
        mqtt.put("clientId", mqttGateway.instanceClientId());
        mqtt.put("brokerUrl", mqttGateway.getBrokerUrl());
        mqtt.put("connected", mqttGateway.isConnected());
        mqtt.put("lastConnectedAt", mqttGateway.getLastConnectedAt());
        mqtt.put("sharedGroup", mqttProperties.getSharedGroup());
        mqtt.put("sharedSubscription", mqttGateway.isSharedSubscription());
        mqtt.put("dedupeEnabled", mqttProperties.isDedupeEnabled());
        mqtt.put("dedupeTtlSeconds", mqttProperties.getDedupeTtlSeconds());
        mqtt.put("messagesReceived", dedupService.receivedCount());
        mqtt.put("duplicatesSkipped", dedupService.skippedCount());

        Map<String, Object> websocket = new LinkedHashMap<>();
        websocket.put("localClients", eventStreamService.onlineClients());
        websocket.put("crossInstanceBroadcast", clusterProperties.isWsBroadcast());
        websocket.put("channel", clusterProperties.getWsChannel());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", node.id());
        result.put("mqtt", mqtt);
        result.put("websocket", websocket);
        return AjaxResult.success(result);
    }

    /** 与 /node 同一个权限位：多实例排查时先看这个进程装的是社区版还是标准版实现。 */
    @PreAuthorize("@ss.hasPermi('simulator:list')")
    @GetMapping("/spi")
    public AjaxResult spi() {
        return AjaxResult.success(spiSnapshotService.snapshot());
    }

    /**
     * 本实例的定时任务运行统计（§54）。只在本进程累计、重启归零，
     * 跨实例的读法在标准版「集群监控」页（同一份摘要随实例心跳带出）。
     */
    @PreAuthorize("@ss.hasPermi('simulator:list')")
    @GetMapping("/tasks")
    public AjaxResult tasks() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", node.id());
        result.put("processLocal", true);
        result.put("schedulers", schedulers());
        result.putAll(taskStats.snapshot());
        return AjaxResult.success(result);
    }

    /** 线程名前缀与池大小是「调度池改没改到位」的直接读数。 */
    private Map<String, Object> schedulers() {
        Map<String, Object> schedulers = new LinkedHashMap<>();
        schedulers.put("main", pool(taskScheduler));
        schedulers.put("report", pool(reportTaskScheduler));
        return schedulers;
    }

    private Map<String, Object> pool(TaskScheduler scheduler) {
        Map<String, Object> pool = new LinkedHashMap<>();
        if (!(scheduler instanceof ThreadPoolTaskScheduler threadPool)) {
            pool.put("type", scheduler.getClass().getSimpleName());
            return pool;
        }
        pool.put("threadNamePrefix", threadPool.getThreadNamePrefix());
        // 配置容量与实际存活线程分开给：池是惰性建线程的，两个数不等只说明还没排到那么多任务
        pool.put("corePoolSize", threadPool.getScheduledThreadPoolExecutor().getCorePoolSize());
        pool.put("poolSize", threadPool.getPoolSize());
        return pool;
    }
}
