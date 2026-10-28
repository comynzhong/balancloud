package com.balancloud.monitor;

import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.metric.TdengineClient;
import com.balancloud.metric.TdengineProperties;
import com.balancloud.mqtt.MqttGateway;
import com.balancloud.mqtt.config.MqttProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * 本实例的中间件连通性与 JVM 状态。只做读探测，不写业务数据，也不回显任何凭据。
 */
@Service
public class NodeStatusService {

    private static final long MEBIBYTE = 1024L * 1024L;

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final MqttGateway mqttGateway;
    private final MqttProperties mqttProperties;
    private final TdengineClient tdengineClient;
    private final TdengineProperties tdengineProperties;
    private final ClusterNode node;
    private final Environment environment;
    private final String edition;
    private final String redisHost;
    private final int redisPort;

    public NodeStatusService(DataSource dataSource,
                             StringRedisTemplate redisTemplate,
                             MqttGateway mqttGateway,
                             MqttProperties mqttProperties,
                             TdengineClient tdengineClient,
                             TdengineProperties tdengineProperties,
                             ClusterNode node,
                             Environment environment,
                             @Value("${balan.edition:community}") String edition,
                             @Value("${spring.data.redis.host:localhost}") String redisHost,
                             @Value("${spring.data.redis.port:6379}") int redisPort) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.mqttGateway = mqttGateway;
        this.mqttProperties = mqttProperties;
        this.tdengineClient = tdengineClient;
        this.tdengineProperties = tdengineProperties;
        this.node = node;
        this.environment = environment;
        this.edition = edition;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", LocalDateTime.now());
        result.put("instance", instance());
        result.put("middlewares", List.of(mysql(), redis(), mqtt(), tdengine()));
        result.put("jvm", jvm());
        return result;
    }

    private Map<String, Object> instance() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("instanceId", node.id());
        info.put("host", hostName());
        info.put("pid", ProcessHandle.current().pid());
        info.put("edition", edition);
        info.put("profiles", List.of(environment.getActiveProfiles()));
        info.put("startedAt", LocalDateTime.ofInstant(
                Instant.ofEpochMilli(runtime.getStartTime()), ZoneId.systemDefault()));
        info.put("uptimeSeconds", runtime.getUptime() / 1000);
        return info;
    }

    private Map<String, Object> mysql() {
        String target = dataSource instanceof HikariDataSource hikari ? safeJdbcUrl(hikari.getJdbcUrl()) : "JDBC";
        return check("mysql", "MySQL", true, target, () -> {
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.isValid(2)) {
                    throw new SQLException("连接校验未通过");
                }
                DatabaseMetaData meta = connection.getMetaData();
                return meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
            }
        });
    }

    private Map<String, Object> redis() {
        return check("redis", "Redis", true, redisHost + ":" + redisPort, () -> {
            String detail = redisTemplate.execute((RedisCallback<String>) connection -> {
                connection.ping();
                Properties info = connection.serverCommands().info("server");
                String version = info == null ? null : info.getProperty("redis_version");
                return version == null ? "PONG" : "Redis " + version;
            });
            return detail == null ? "PONG" : detail;
        });
    }

    private Map<String, Object> mqtt() {
        return check("emqx", "EMQX", mqttProperties.isEnabled(), safeBroker(mqttGateway.getBrokerUrl()), () -> {
            if (!mqttGateway.isConnected()) {
                throw new IllegalStateException("未连接 broker");
            }
            return (mqttGateway.isSharedSubscription() ? "共享订阅" : "普通订阅")
                    + " · " + mqttGateway.instanceClientId();
        });
    }

    private Map<String, Object> tdengine() {
        return check("tdengine", "TDengine", tdengineProperties.isEnabled(), tdengineProperties.getUrl(),
                () -> "TDengine " + tdengineClient.ping());
    }

    private Map<String, Object> check(String key, String name, boolean enabled, String target, Callable<String> probe) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("name", name);
        item.put("enabled", enabled);
        item.put("target", target);
        if (!enabled) {
            item.put("up", false);
            item.put("latencyMs", null);
            item.put("detail", "未启用");
            item.put("error", null);
            return item;
        }
        long startedAt = System.nanoTime();
        try {
            item.put("up", true);
            item.put("detail", probe.call());
            item.put("error", null);
        } catch (Exception e) {
            item.put("up", false);
            item.put("detail", null);
            item.put("error", message(e));
        }
        item.put("latencyMs", Math.max(1, (System.nanoTime() - startedAt) / 1_000_000));
        return item;
    }

    private Map<String, Object> jvm() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(gc.getCollectionCount(), 0);
            gcTime += Math.max(gc.getCollectionTime(), 0);
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        info.put("startedAt", LocalDateTime.ofInstant(
                Instant.ofEpochMilli(runtime.getStartTime()), ZoneId.systemDefault()));
        info.put("uptimeSeconds", runtime.getUptime() / 1000);

        info.put("heapUsedMb", mb(heap.getUsed()));
        info.put("heapCommittedMb", mb(heap.getCommitted()));
        info.put("heapMaxMb", heap.getMax() > 0 ? mb(heap.getMax()) : null);
        info.put("heapUsedPercent", heap.getMax() > 0
                ? percent(heap.getUsed(), heap.getMax()) : null);
        info.put("nonHeapUsedMb", mb(nonHeap.getUsed()));
        info.put("threadCount", threads.getThreadCount());
        info.put("peakThreadCount", threads.getPeakThreadCount());
        info.put("daemonThreadCount", threads.getDaemonThreadCount());
        info.put("gcCount", gcCount);
        info.put("gcTimeMs", gcTime);

        Double processCpu = null;
        Double systemCpu = null;
        Double loadAverage = null;
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            if (sun.getProcessCpuLoad() >= 0) {
                processCpu = round1(sun.getProcessCpuLoad() * 100);
            }
            if (sun.getCpuLoad() >= 0) {
                systemCpu = round1(sun.getCpuLoad() * 100);
            }
        }
        if (os.getSystemLoadAverage() >= 0) {
            loadAverage = round1(os.getSystemLoadAverage());
        }
        info.put("processCpuPercent", processCpu);
        info.put("systemCpuPercent", systemCpu);
        info.put("systemLoadAverage", loadAverage);
        return info;
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** JDBC URL 可能带 user/password 参数，监控页只展示到库名。 */
    static String safeJdbcUrl(String url) {
        if (url == null) {
            return "JDBC";
        }
        int query = url.indexOf('?');
        String cleaned = query >= 0 ? url.substring(0, query) : url;
        return cleaned.replaceAll("(?i)(password|user)=[^&;]*", "$1=***");
    }

    /** Broker URL 可能内嵌用户名密码，保留协议与主机端口即可判断连通目标。 */
    static String safeBroker(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("://[^/@]+@", "://");
    }

    private String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private double mb(long bytes) {
        return round1(bytes / (double) MEBIBYTE);
    }

    private double percent(long used, long max) {
        return round1(used * 100.0 / max);
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
