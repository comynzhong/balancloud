package com.balancloud.metric;

import com.balancloud.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class TdengineClient {

    private static final Logger log = LoggerFactory.getLogger(TdengineClient.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final TdengineProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final RestClient pingClient;

    public TdengineClient(TdengineProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String credentials = properties.getUsername() + ":" + properties.getPassword();
        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
                .build();
        // 监控页每 30 秒探活一次，不能复用业务客户端：业务查询允许慢，探活必须快速失败。
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory pingFactory = new JdkClientHttpRequestFactory(httpClient);
        pingFactory.setReadTimeout(Duration.ofSeconds(2));
        this.pingClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
                .requestFactory(pingFactory)
                .build();
    }

    /** 只用于健康检查：不依赖具体数据库是否存在，查询服务端版本即可确认链路。 */
    public String ping() {
        if (!properties.isEnabled()) {
            throw new ServiceException("时序数据库未启用");
        }
        try {
            String response = pingClient.post()
                    .uri("/rest/sql")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("SELECT SERVER_VERSION()")
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new ServiceException("时序数据库返回错误: " + root.path("desc").asText("未知错误"));
            }
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty() && data.get(0).isArray() && !data.get(0).isEmpty()) {
                return data.get(0).get(0).asText();
            }
            return "unknown";
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("TDengine ping failed: {}", e.getMessage());
            throw new ServiceException("时序数据库连接失败");
        }
    }

    public void insert(List<MetricPoint> points) {
        if (!properties.isEnabled() || points == null || points.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        for (int i = 0; i < points.size(); i++) {
            MetricPoint point = points.get(i);
            if (i > 0) {
                sql.append(' ');
            }
            sql.append('d').append(point.chargerId()).append("_p").append(point.portNo())
                    .append(" USING device_metric TAGS(")
                    .append(point.stationId()).append(',').append(point.chargerId()).append(',').append(point.portNo())
                    .append(',').append(point.tenantId())
                    .append(") VALUES('").append(point.ts().format(TS_FORMAT)).append("',")
                    .append(format(point.voltage())).append(',')
                    .append(format(point.currentA())).append(',')
                    .append(format(point.temperature())).append(',')
                    .append(format(point.powerKw())).append(',')
                    .append(format(point.energyTotal())).append(',')
                    .append(point.signalStrength()).append(')');
        }
        execute(sql.toString());
    }

    public List<MetricHistoryPointVo> queryHistory(long chargerId, int portNo, int minutes, int limit) {
        int safeMinutes = Math.min(Math.max(minutes, 1), 1440);
        int safeLimit = Math.min(Math.max(limit, 1), 5000);
        String start = LocalDateTime.now(ZONE).minusMinutes(safeMinutes).format(TS_FORMAT);
        String sql = "SELECT ts, voltage, current_a, temperature, power_kw, energy_total " +
                "FROM device_metric WHERE charger_id = " + chargerId +
                " AND port_no = " + portNo +
                " AND ts >= '" + start + "'" +
                " ORDER BY ts ASC LIMIT " + safeLimit;
        JsonNode root = execute(sql);
        List<MetricHistoryPointVo> points = new ArrayList<>();
        for (JsonNode row : root.path("data")) {
            long ts = parseTimestamp(row.path(0).asText());
            points.add(new MetricHistoryPointVo(
                    ts,
                    row.path(1).isNull() ? null : row.path(1).asDouble(),
                    row.path(2).isNull() ? null : row.path(2).asDouble(),
                    row.path(3).isNull() ? null : row.path(3).asDouble(),
                    row.path(4).isNull() ? null : row.path(4).asDouble(),
                    row.path(5).isNull() ? null : row.path(5).asDouble()
            ));
        }
        return points;
    }

    /** 批量查询的设备数上限（§60.3）：一桩多口再加同站对比，20 台够一次诊断用；再宽就是拿时序库当全库扫。 */
    public static final int MAX_BATCH_CHARGERS = 20;

    /** 批量查询的时间窗上限，与单设备曲线同一个 1440 分钟。 */
    public static final int MAX_BATCH_MINUTES = 1440;

    /** 批量查询的总点数上限，与单设备曲线同一个 5000。 */
    public static final int MAX_BATCH_POINTS = 5000;

    /**
     * 按设备 ID 集合批量取测点（§60.3）。与 {@link #queryHistory} 有两处刻意不同：
     *
     * <p>一是超限<b>明确抛 400</b>而不是 {@code Math.min} 静默夹。调用方是机器消费者，
     * 静默截断会把「只回了 20 台」读成「这个站只有 20 台」，得出错的结论；单设备曲线夹一下没人受影响。
     *
     * <p>二是这里的 {@code chargerIds} 必须是调用方<b>先过 MySQL 场站范围校验</b>解析出来的合法集合：
     * 本方法的 SQL 只有 {@code charger_id IN (...)}，时序库这一层没有租户与场站概念，
     * 直接吃调用方给的设备号等于绕过 {@code StationScopeService}（§60.3 写死的隔离顺序）。
     */
    public List<MetricSeriesPoint> queryBatch(List<Long> chargerIds, Integer portNo, int minutes, int limit) {
        if (chargerIds == null || chargerIds.isEmpty()) {
            throw new ServiceException(400, "至少指定一台设备");
        }
        if (chargerIds.size() > MAX_BATCH_CHARGERS) {
            throw new ServiceException(400, "一次最多查 " + MAX_BATCH_CHARGERS + " 台设备，当前给了 " + chargerIds.size() + " 台");
        }
        if (minutes < 1 || minutes > MAX_BATCH_MINUTES) {
            throw new ServiceException(400, "时间窗必须在 1 到 " + MAX_BATCH_MINUTES + " 分钟之间");
        }
        if (limit < 1 || limit > MAX_BATCH_POINTS) {
            throw new ServiceException(400, "返回点数必须在 1 到 " + MAX_BATCH_POINTS + " 之间");
        }
        String start = LocalDateTime.now(ZONE).minusMinutes(minutes).format(TS_FORMAT);
        String inList = chargerIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String portFilter = portNo == null ? "" : " AND port_no = " + portNo;
        String sql = "SELECT ts, charger_id, port_no, voltage, current_a, temperature, power_kw, energy_total "
                + "FROM device_metric WHERE charger_id IN (" + inList + ")"
                + " AND ts >= '" + start + "'" + portFilter
                + " ORDER BY charger_id ASC, ts ASC LIMIT " + limit;
        JsonNode root = execute(sql);
        List<MetricSeriesPoint> points = new ArrayList<>();
        for (JsonNode row : root.path("data")) {
            points.add(new MetricSeriesPoint(
                    row.path(1).asLong(),
                    row.path(2).asInt(),
                    parseTimestamp(row.path(0).asText()),
                    row.path(3).isNull() ? null : row.path(3).asDouble(),
                    row.path(4).isNull() ? null : row.path(4).asDouble(),
                    row.path(5).isNull() ? null : row.path(5).asDouble(),
                    row.path(6).isNull() ? null : row.path(6).asDouble(),
                    row.path(7).isNull() ? null : row.path(7).asDouble()
            ));
        }
        return points;
    }

    private JsonNode execute(String sql) {
        if (!properties.isEnabled()) {
            throw new ServiceException("时序数据库未启用");
        }
        try {
            String response = restClient.post()
                    .uri("/rest/sql/{db}", properties.getDatabase())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(sql)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new ServiceException("时序数据库执行失败: " + root.path("desc").asText("未知错误"));
            }
            return root;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("TDengine request failed: {}", e.getMessage());
            throw new ServiceException("时序数据库连接失败");
        }
    }

    private long parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // TDengine REST may return local time without timezone.
        }
        try {
            LocalDateTime local = LocalDateTime.parse(value.replace(' ', 'T'));
            return local.atZone(ZONE).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            log.warn("Unrecognized TDengine timestamp: {}", value);
            return 0L;
        }
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
