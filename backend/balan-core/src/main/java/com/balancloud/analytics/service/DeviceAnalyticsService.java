package com.balancloud.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.balancloud.analytics.vo.DeviceAnalyticsVo;
import com.balancloud.analytics.vo.DeviceAnalyticsVo.DeviceAnalyticsRowVo;
import com.balancloud.analytics.vo.DeviceAnalyticsVo.DeviceAnalyticsSummaryVo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.mapper.ChargeOrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备基础分析（core）：端口利用率、故障率与平均时长。
 *
 * <p>利用率 = 区间内订单累计充电时长 ÷（端口数 × 区间秒数）；故障率 = 故障类结束订单 ÷ 订单数。
 * 时段热力、效率排名、故障预测属标准版 jar 深度分析，不在本服务内。
 */
@Service
public class DeviceAnalyticsService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ChargeOrderMapper orderMapper;
    private final ChargerMapper chargerMapper;
    private final ChargerPortMapper portMapper;
    private final StationMapper stationMapper;
    private final StationScopeService scopeService;

    public DeviceAnalyticsService(ChargeOrderMapper orderMapper,
                                  ChargerMapper chargerMapper,
                                  ChargerPortMapper portMapper,
                                  StationMapper stationMapper,
                                  StationScopeService scopeService) {
        this.orderMapper = orderMapper;
        this.chargerMapper = chargerMapper;
        this.portMapper = portMapper;
        this.stationMapper = stationMapper;
        this.scopeService = scopeService;
    }

    public DeviceAnalyticsVo device(LocalDate startDate, LocalDate endDate, Long stationId) {
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(6) : startDate;
        if (start.isAfter(end)) {
            throw new ServiceException(400, "开始日期不能晚于结束日期");
        }
        if (Duration.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()).toDays() > MAX_RANGE_DAYS) {
            throw new ServiceException(400, "统计区间不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        DeviceAnalyticsVo result = new DeviceAnalyticsVo();
        result.setStartDate(start);
        result.setEndDate(end);
        DeviceAnalyticsSummaryVo summary = new DeviceAnalyticsSummaryVo();
        result.setSummary(summary);
        result.setDevices(Collections.emptyList());

        List<Long> stationIds = resolveScope(stationId);
        if (stationIds.isEmpty()) {
            applyZero(summary, 0, 0);
            return result;
        }
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        long windowSeconds = Duration.between(from, to).getSeconds();

        List<Charger> chargers = chargerMapper.selectList(new LambdaQueryWrapper<Charger>()
                .in(Charger::getStationId, stationIds)
                .orderByAsc(Charger::getStationId)
                .orderByAsc(Charger::getId));
        if (chargers.isEmpty()) {
            applyZero(summary, 0, 0);
            return result;
        }
        List<Long> chargerIds = chargers.stream().map(Charger::getId).toList();
        Map<Long, Integer> portCounts = new HashMap<>();
        Map<Long, Integer> faultPorts = new HashMap<>();
        for (ChargerPort port : portMapper.selectList(new LambdaQueryWrapper<ChargerPort>()
                .in(ChargerPort::getChargerId, chargerIds))) {
            portCounts.merge(port.getChargerId(), 1, Integer::sum);
            if ("FAULT".equals(port.getStatus())) {
                faultPorts.merge(port.getChargerId(), 1, Integer::sum);
            }
        }
        Map<Long, Station> stations = stationMapper.selectBatchIds(
                        chargers.stream().map(Charger::getStationId).distinct().toList())
                .stream().collect(Collectors.toMap(Station::getId, Function.identity()));

        QueryWrapper<ChargeOrder> query = new QueryWrapper<ChargeOrder>()
                .select("charger_id AS cid",
                        "COUNT(*) AS order_count",
                        "IFNULL(SUM(duration_seconds), 0) AS duration_seconds",
                        "IFNULL(SUM(energy_kwh), 0) AS energy_kwh",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE total_fee END), 0) AS revenue",
                        "SUM(CASE WHEN stop_reason IN ('FAULT', 'DEVICE_OFFLINE', 'STOP_TIMEOUT') "
                                + "THEN 1 ELSE 0 END) AS fault_orders",
                        "IFNULL(AVG(CASE WHEN duration_seconds > 0 THEN duration_seconds END), 0) "
                                + "AS avg_duration_seconds")
                .ge("start_time", from)
                .lt("start_time", to)
                .ne("status", "FAILED")
                .in("station_id", stationIds)
                .groupBy("charger_id");
        Map<Long, Map<String, Object>> statsByCharger = new HashMap<>();
        for (Map<String, Object> row : orderMapper.selectMaps(query)) {
            Long chargerId = asLong(row, "cid");
            if (chargerId != null) {
                statsByCharger.put(chargerId, row);
            }
        }

        List<DeviceAnalyticsRowVo> rows = new ArrayList<>();
        long totalPorts = 0;
        long totalOrders = 0;
        long totalFaultOrders = 0;
        long totalDuration = 0;
        long totalFaultPorts = 0;
        BigDecimal totalEnergy = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalAvgDuration = BigDecimal.ZERO;
        int devicesWithOrders = 0;
        for (Charger charger : chargers) {
            int portCount = portCounts.getOrDefault(charger.getId(), 0);
            if (portCount == 0) {
                continue;
            }
            DeviceAnalyticsRowVo row = new DeviceAnalyticsRowVo();
            row.setChargerId(charger.getId());
            row.setDeviceNo(charger.getDeviceNo());
            row.setDeviceName(charger.getName());
            row.setStationId(charger.getStationId());
            Station station = stations.get(charger.getStationId());
            row.setStationName(station == null ? null : station.getName());
            row.setPortCount(portCount);
            row.setFaultPorts(faultPorts.getOrDefault(charger.getId(), 0));

            Map<String, Object> stats = statsByCharger.get(charger.getId());
            long orderCount = stats == null ? 0 : nzLong(stats, "order_count");
            long duration = stats == null ? 0 : nzLong(stats, "duration_seconds");
            long faultOrders = stats == null ? 0 : nzLong(stats, "fault_orders");
            BigDecimal energy = stats == null ? BigDecimal.ZERO : asDecimal(stats, "energy_kwh");
            BigDecimal revenue = stats == null ? BigDecimal.ZERO : asDecimal(stats, "revenue");
            BigDecimal avgDuration = stats == null ? BigDecimal.ZERO : asDecimal(stats, "avg_duration_seconds");

            row.setOrderCount(orderCount);
            row.setChargingHours(hours(duration));
            row.setUtilizationRate(utilization(duration, portCount, windowSeconds));
            row.setFaultOrders(faultOrders);
            row.setFaultRate(percent(faultOrders, orderCount));
            row.setAvgDurationMinutes(minutes(avgDuration));
            row.setEnergyKwh(energy.setScale(2, RoundingMode.HALF_UP));
            row.setRevenue(revenue.setScale(2, RoundingMode.HALF_UP));
            rows.add(row);

            totalPorts += portCount;
            totalOrders += orderCount;
            totalFaultOrders += faultOrders;
            totalDuration += duration;
            totalEnergy = totalEnergy.add(energy);
            totalRevenue = totalRevenue.add(revenue);
            totalFaultPorts += row.getFaultPorts();
            if (orderCount > 0) {
                totalAvgDuration = totalAvgDuration.add(avgDuration.multiply(BigDecimal.valueOf(orderCount)));
                devicesWithOrders += orderCount;
            }
        }

        summary.setChargerCount(rows.size());
        summary.setPortCount((int) totalPorts);
        summary.setOrderCount(totalOrders);
        summary.setChargingHours(hours(totalDuration));
        summary.setUtilizationRate(utilization(totalDuration, totalPorts, windowSeconds));
        summary.setFaultOrders(totalFaultOrders);
        summary.setFaultRate(percent(totalFaultOrders, totalOrders));
        summary.setAvgDurationMinutes(devicesWithOrders == 0 ? BigDecimal.ZERO
                : minutes(totalAvgDuration.divide(BigDecimal.valueOf(devicesWithOrders), 4, RoundingMode.HALF_UP)));
        summary.setEnergyKwh(totalEnergy.setScale(2, RoundingMode.HALF_UP));
        summary.setRevenue(totalRevenue.setScale(2, RoundingMode.HALF_UP));
        summary.setFaultPorts(totalFaultPorts);

        rows.sort(Comparator.comparing(DeviceAnalyticsRowVo::getUtilizationRate).reversed()
                .thenComparing(row -> row.getDeviceNo() == null ? "" : row.getDeviceNo()));
        result.setDevices(rows);
        return result;
    }

    private List<Long> resolveScope(Long stationId) {
        if (stationId != null) {
            scopeService.assertAccess(stationId);
            return List.of(stationId);
        }
        List<Long> authorized = scopeService.authorizedStationIds();
        if (authorized != null) {
            return authorized;
        }
        return stationMapper.selectList(new LambdaQueryWrapper<Station>().select(Station::getId))
                .stream().map(Station::getId).toList();
    }

    private void applyZero(DeviceAnalyticsSummaryVo summary, int chargers, int ports) {
        summary.setChargerCount(chargers);
        summary.setPortCount(ports);
        summary.setOrderCount(0);
        summary.setChargingHours(BigDecimal.ZERO);
        summary.setUtilizationRate(BigDecimal.ZERO);
        summary.setFaultOrders(0);
        summary.setFaultRate(BigDecimal.ZERO);
        summary.setAvgDurationMinutes(BigDecimal.ZERO);
        summary.setEnergyKwh(BigDecimal.ZERO);
        summary.setRevenue(BigDecimal.ZERO);
        summary.setFaultPorts(0);
    }

    private BigDecimal utilization(long durationSeconds, long portCount, long windowSeconds) {
        if (portCount <= 0 || windowSeconds <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = BigDecimal.valueOf(durationSeconds)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(portCount * windowSeconds), 2, RoundingMode.HALF_UP);
        return rate.min(BigDecimal.valueOf(100));
    }

    private BigDecimal percent(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal hours(long seconds) {
        return BigDecimal.valueOf(seconds)
                .divide(BigDecimal.valueOf(SECONDS_PER_HOUR), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal minutes(BigDecimal seconds) {
        return seconds.divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    private long nzLong(Map<String, Object> row, String column) {
        Object value = value(row, column);
        return value instanceof Number number ? number.longValue()
                : (value == null ? 0L : Long.parseLong(String.valueOf(value)));
    }

    private BigDecimal asDecimal(Map<String, Object> row, String column) {
        Object value = value(row, column);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }

    private Long asLong(Map<String, Object> row, String column) {
        Object value = value(row, column);
        return value instanceof Number number ? number.longValue()
                : (value == null ? null : Long.parseLong(String.valueOf(value)));
    }

    private Object value(Map<String, Object> row, String column) {
        Object direct = row.get(column);
        if (direct != null) {
            return direct;
        }
        String normalized = column.replace("_", "").toLowerCase();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().replace("_", "").toLowerCase().equals(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
