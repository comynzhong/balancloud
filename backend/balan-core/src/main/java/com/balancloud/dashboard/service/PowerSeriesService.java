package com.balancloud.dashboard.service;

import com.balancloud.dashboard.vo.PowerPointVo;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.framework.tenant.TenantTaskExecutor;
import com.balancloud.mqtt.DeviceShadowService;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.ws.EventStreamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 场站实时功率序列：每 10 秒按设备影子汇总一次总功率，写入 Redis 环形列表并推送 WebSocket。
 * 看板曲线读这段历史，避免每次刷新都对 TDengine 做全端口聚合。
 */
@Service
public class PowerSeriesService {

    private static final Logger log = LoggerFactory.getLogger(PowerSeriesService.class);
    private static final String SERIES_PREFIX = "balan:power:";
    private static final long TICK_SECONDS = 10;
    private static final int MAX_POINTS = 720;
    private static final Duration SERIES_TTL = Duration.ofHours(26);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ChargerMapper chargerMapper;
    private final DeviceShadowService shadowService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EventStreamService eventStreamService;
    private final TenantTaskExecutor tenantTasks;

    public PowerSeriesService(ChargerMapper chargerMapper,
                              DeviceShadowService shadowService,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              EventStreamService eventStreamService,
                              TenantTaskExecutor tenantTasks) {
        this.chargerMapper = chargerMapper;
        this.shadowService = shadowService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.eventStreamService = eventStreamService;
        this.tenantTasks = tenantTasks;
    }

    @Scheduled(fixedDelay = TICK_SECONDS * 1000, initialDelay = 20_000)
    public void sample() {
        // 两个实例都采样会把 10 秒栅格写成双份功率点，曲线窗口直接减半
        tenantTasks.forEach("power-sample", Duration.ofSeconds(TICK_SECONDS),
                Duration.ofSeconds(TICK_SECONDS - 1), this::sampleForTenant);
    }

    private void sampleForTenant() {
        List<Charger> chargers = chargerMapper.selectList(new LambdaQueryWrapper<Charger>()
                .select(Charger::getId, Charger::getStationId, Charger::getDeviceNo, Charger::getOnlineStatus));
        if (chargers.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> powerByStation = new LinkedHashMap<>();
        Map<Long, Long> chargingByStation = new LinkedHashMap<>();
        for (Charger charger : chargers) {
            Long stationId = charger.getStationId();
            if (stationId == null) {
                continue;
            }
            powerByStation.putIfAbsent(stationId, ZERO);
            chargingByStation.putIfAbsent(stationId, 0L);
            if (!"ONLINE".equals(charger.getOnlineStatus())) {
                continue;
            }
            for (PortMetric metric : shadowService.get(charger.getDeviceNo()).values()) {
                double power = metric.getPowerKw() == null ? 0D : metric.getPowerKw();
                powerByStation.merge(stationId, BigDecimal.valueOf(power), BigDecimal::add);
                if ("CHARGING".equals(metric.getStatus())) {
                    chargingByStation.merge(stationId, 1L, Long::sum);
                }
            }
        }
        long ts = System.currentTimeMillis() / 1000 / TICK_SECONDS * TICK_SECONDS;
        for (Map.Entry<Long, BigDecimal> entry : powerByStation.entrySet()) {
            Long stationId = entry.getKey();
            BigDecimal power = entry.getValue().setScale(3, RoundingMode.HALF_UP);
            long chargingPorts = chargingByStation.getOrDefault(stationId, 0L);
            PowerPointVo point = new PowerPointVo(ts, power, chargingPorts);
            writePoint(stationId, point);
            publish(stationId, point);
        }
    }

    /** 返回按时间升序的功率点；多场站时按采样时间栅格合并求和。 */
    public List<PowerPointVo> trend(List<Long> stationIds, int minutes) {
        int points = (int) Math.min(MAX_POINTS, Math.max(1, Math.ceil(minutes * 60D / TICK_SECONDS)));
        if (stationIds == null || stationIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (stationIds.size() == 1) {
            return read(stationIds.get(0), points);
        }
        Map<Long, PowerPointVo> merged = new TreeMap<>();
        for (Long stationId : stationIds) {
            for (PowerPointVo point : read(stationId, points)) {
                PowerPointVo current = merged.get(point.getTs());
                if (current == null) {
                    merged.put(point.getTs(), new PowerPointVo(point.getTs(), point.getPowerKw(), point.getChargingPorts()));
                } else {
                    current.setPowerKw(current.getPowerKw().add(point.getPowerKw()));
                    current.setChargingPorts(current.getChargingPorts() + point.getChargingPorts());
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    public PowerPointVo latest(List<Long> stationIds) {
        if (stationIds == null || stationIds.isEmpty()) {
            return null;
        }
        BigDecimal power = BigDecimal.ZERO;
        long chargingPorts = 0;
        Long ts = null;
        for (Long stationId : stationIds) {
            List<PowerPointVo> points = read(stationId, 1);
            if (points.isEmpty()) {
                continue;
            }
            PowerPointVo point = points.get(0);
            power = power.add(point.getPowerKw());
            chargingPorts += point.getChargingPorts();
            ts = ts == null ? point.getTs() : Math.max(ts, point.getTs());
        }
        return ts == null ? null : new PowerPointVo(ts, power.setScale(3, RoundingMode.HALF_UP), chargingPorts);
    }

    private List<PowerPointVo> read(Long stationId, int points) {
        try {
            List<String> raw = redisTemplate.opsForList().range(SERIES_PREFIX + stationId, 0, points - 1);
            if (raw == null || raw.isEmpty()) {
                return Collections.emptyList();
            }
            List<PowerPointVo> result = new ArrayList<>(raw.size());
            for (int i = raw.size() - 1; i >= 0; i--) {
                PowerPointVo point = parse(raw.get(i));
                if (point != null) {
                    result.add(point);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to read power series of station {}: {}", stationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void writePoint(Long stationId, PowerPointVo point) {
        try {
            String key = SERIES_PREFIX + stationId;
            redisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(point));
            redisTemplate.opsForList().trim(key, 0, MAX_POINTS - 1);
            redisTemplate.expire(key, SERIES_TTL);
        } catch (Exception e) {
            log.warn("Failed to write power series of station {}: {}", stationId, e.getMessage());
        }
    }

    private void publish(Long stationId, PowerPointVo point) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stationId", stationId);
        data.put("ts", point.getTs());
        data.put("powerKw", point.getPowerKw());
        data.put("chargingPorts", point.getChargingPorts());
        eventStreamService.publish("power", stationId, data);
    }

    private PowerPointVo parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new PowerPointVo(
                    node.path("ts").asLong(),
                    new BigDecimal(node.path("powerKw").asText("0")),
                    node.path("chargingPorts").asLong());
        } catch (Exception e) {
            log.debug("Skip malformed power series point: {}", e.getMessage());
            return null;
        }
    }
}
