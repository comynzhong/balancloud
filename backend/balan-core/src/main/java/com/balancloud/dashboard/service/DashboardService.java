package com.balancloud.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.balancloud.alert.domain.AlertRecord;
import com.balancloud.alert.mapper.AlertRecordMapper;
import com.balancloud.alert.model.AlertLevel;
import com.balancloud.alert.model.AlertStatus;
import com.balancloud.alert.model.AlertType;
import com.balancloud.dashboard.vo.AlertStatsVo;
import com.balancloud.dashboard.vo.BusinessTrendPointVo;
import com.balancloud.dashboard.vo.DashboardEventVo;
import com.balancloud.dashboard.vo.DashboardSummaryVo;
import com.balancloud.dashboard.vo.EnergyPointVo;
import com.balancloud.dashboard.vo.NameCountVo;
import com.balancloud.dashboard.vo.PowerPointVo;
import com.balancloud.dashboard.vo.StationOverviewVo;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.device.vo.ChargerGroupStats;
import com.balancloud.device.vo.StationStats;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.ws.EventStreamService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 运营看板统计：全部按当前账号的场站授权范围收敛，超级管理员为全站口径。
 */
@Service
public class DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int EVENT_LIMIT = 30;

    private final StationMapper stationMapper;
    private final ChargerMapper chargerMapper;
    private final ChargeOrderMapper orderMapper;
    private final AlertRecordMapper alertMapper;
    private final PowerSeriesService powerSeriesService;
    private final StationScopeService scopeService;
    private final EventStreamService eventStreamService;

    public DashboardService(StationMapper stationMapper,
                            ChargerMapper chargerMapper,
                            ChargeOrderMapper orderMapper,
                            AlertRecordMapper alertMapper,
                            PowerSeriesService powerSeriesService,
                            StationScopeService scopeService,
                            EventStreamService eventStreamService) {
        this.stationMapper = stationMapper;
        this.chargerMapper = chargerMapper;
        this.orderMapper = orderMapper;
        this.alertMapper = alertMapper;
        this.powerSeriesService = powerSeriesService;
        this.scopeService = scopeService;
        this.eventStreamService = eventStreamService;
    }

    public DashboardSummaryVo summary(Long stationId) {
        List<Long> ids = scope(stationId);
        DashboardSummaryVo vo = new DashboardSummaryVo();
        vo.setStationCount(ids.size());
        vo.setCurrentPowerKw(ZERO);
        fillEmptyAmounts(vo);
        vo.setWsClients(eventStreamService.onlineClients());
        if (ids.isEmpty()) {
            return vo;
        }

        long deviceCount = 0;
        long onlineDeviceCount = 0;
        long portCount = 0;
        long chargingCount = 0;
        long faultCount = 0;
        for (StationStats stats : chargerMapper.selectStatsByStationIds(ids)) {
            deviceCount += stats.getDeviceCount();
            onlineDeviceCount += stats.getOnlineDeviceCount();
            portCount += stats.getPortCount();
            chargingCount += stats.getChargingPortCount();
            faultCount += stats.getFaultPortCount();
        }
        vo.setDeviceCount(deviceCount);
        vo.setOnlineDeviceCount(onlineDeviceCount);
        vo.setPortCount(portCount);
        vo.setChargingPortCount(chargingCount);
        vo.setFaultPortCount(faultCount);
        vo.setIdlePortCount(Math.max(portCount - chargingCount - faultCount, 0));

        PowerPointVo latestPower = powerSeriesService.latest(ids);
        if (latestPower != null) {
            vo.setCurrentPowerKw(latestPower.getPowerKw());
            vo.setPowerSampleAt(latestPower.getTs());
        }

        Map<String, Object> today = aggregate(ids, todayStart(), null);
        vo.setTodayOrderCount(asLong(today, "order_count"));
        vo.setTodayEnergyKwh(asDecimal(today, "energy_kwh"));
        vo.setTodayRevenue(scale2(asDecimal(today, "revenue")));
        vo.setTodayEnergyFee(scale2(asDecimal(today, "energy_fee")));
        vo.setTodayServiceFee(scale2(asDecimal(today, "service_fee")));
        vo.setTodayUnpaidCount(asLong(today, "unpaid_count"));
        vo.setTodayRefundCount(asLong(today, "refund_count"));
        vo.setTodayRefundAmount(scale2(asDecimal(today, "refund_amount")));

        Map<String, Object> yesterday = aggregate(ids, todayStart().minusDays(1), todayStart());
        vo.setYesterdayOrderCount(asLong(yesterday, "order_count"));
        vo.setYesterdayEnergyKwh(asDecimal(yesterday, "energy_kwh"));
        vo.setYesterdayRevenue(scale2(asDecimal(yesterday, "revenue")));

        LocalDate todayDate = LocalDate.now();
        LocalDateTime monthStart = todayDate.withDayOfMonth(1).atStartOfDay();
        Map<String, Object> month = aggregate(ids, monthStart, null);
        vo.setMonthOrderCount(asLong(month, "order_count"));
        vo.setMonthEnergyKwh(asDecimal(month, "energy_kwh"));
        vo.setMonthRevenue(scale2(asDecimal(month, "revenue")));
        vo.setMonthEnergyFee(scale2(asDecimal(month, "energy_fee")));
        vo.setMonthServiceFee(scale2(asDecimal(month, "service_fee")));
        vo.setLastMonthRevenue(scale2(asDecimal(
                aggregate(ids, monthStart.minusMonths(1), monthStart), "revenue")));

        LocalDateTime yearStart = todayDate.withDayOfYear(1).atStartOfDay();
        Map<String, Object> year = aggregate(ids, yearStart, null);
        vo.setYearOrderCount(asLong(year, "order_count"));
        vo.setYearEnergyKwh(asDecimal(year, "energy_kwh"));
        vo.setYearRevenue(scale2(asDecimal(year, "revenue")));
        vo.setYearEnergyFee(scale2(asDecimal(year, "energy_fee")));
        vo.setYearServiceFee(scale2(asDecimal(year, "service_fee")));
        LocalDateTime now = LocalDateTime.now();
        vo.setLastYearRevenue(scale2(asDecimal(
                aggregate(ids, yearStart.minusYears(1), now.minusYears(1)), "revenue")));

        vo.setActiveOrderCount(orderMapper.selectCount(new LambdaQueryWrapper<ChargeOrder>()
                .in(ChargeOrder::getStationId, ids)
                .in(ChargeOrder::getStatus, OrderStatus.PENDING_START.name(),
                        OrderStatus.CHARGING.name(), OrderStatus.STOPPING.name())));

        vo.setOpenAlertCount(alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .in(AlertRecord::getStationId, ids)
                .ne(AlertRecord::getStatus, AlertStatus.RESOLVED.name())));
        vo.setCriticalAlertCount(alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .in(AlertRecord::getStationId, ids)
                .eq(AlertRecord::getAlertLevel, AlertLevel.CRITICAL.name())
                .ne(AlertRecord::getStatus, AlertStatus.RESOLVED.name())));
        vo.setTodayAlertCount(alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .in(AlertRecord::getStationId, ids)
                .ge(AlertRecord::getOccurredAt, todayStart())));
        return vo;
    }

    public List<PowerPointVo> powerTrend(Long stationId, int minutes) {
        List<Long> ids = scope(stationId);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return powerSeriesService.trend(ids, Math.min(Math.max(minutes, 5), 240));
    }

    /** 单量区间聚合：口径与今日一致，失败单不计，已退款单不计入营收与电费服务费。 */
    private Map<String, Object> aggregate(List<Long> ids, LocalDateTime from, LocalDateTime to) {
        QueryWrapper<ChargeOrder> query = new QueryWrapper<ChargeOrder>()
                .select("COUNT(*) AS order_count",
                        "IFNULL(SUM(energy_kwh), 0) AS energy_kwh",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE total_fee END), 0) AS revenue",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE energy_fee END), 0) AS energy_fee",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE service_fee END), 0) AS service_fee",
                        "SUM(CASE WHEN payment_status = 'UNPAID' AND status IN ('COMPLETED', 'ABNORMAL') "
                                + "THEN 1 ELSE 0 END) AS unpaid_count",
                        "SUM(CASE WHEN payment_status = 'REFUNDED' THEN 1 ELSE 0 END) AS refund_count",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN total_fee ELSE 0 END), 0) "
                                + "AS refund_amount")
                .ge("start_time", from)
                .ne("status", OrderStatus.FAILED.name())
                .in("station_id", ids);
        if (to != null) {
            query.lt("start_time", to);
        }
        return firstRow(orderMapper.selectMaps(query));
    }

    private void fillEmptyAmounts(DashboardSummaryVo vo) {
        vo.setTodayEnergyKwh(ZERO);
        vo.setTodayRevenue(ZERO);
        vo.setTodayEnergyFee(ZERO);
        vo.setTodayServiceFee(ZERO);
        vo.setTodayRefundAmount(ZERO);
        vo.setYesterdayEnergyKwh(ZERO);
        vo.setYesterdayRevenue(ZERO);
        vo.setMonthEnergyKwh(ZERO);
        vo.setMonthRevenue(ZERO);
        vo.setMonthEnergyFee(ZERO);
        vo.setMonthServiceFee(ZERO);
        vo.setLastMonthRevenue(ZERO);
        vo.setYearEnergyKwh(ZERO);
        vo.setYearRevenue(ZERO);
        vo.setYearEnergyFee(ZERO);
        vo.setYearServiceFee(ZERO);
        vo.setLastYearRevenue(ZERO);
    }

    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 首页「场站明细」：逐场站的资产、实时负荷与今日经营。 */
    public List<StationOverviewVo> stationOverview() {        List<Long> ids = scope(null);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, StationStats> statsById = chargerMapper.selectStatsByStationIds(ids).stream()
                .collect(Collectors.toMap(StationStats::getStationId, Function.identity(), (a, b) -> a));
        Map<Long, Map<String, Object>> todayById = new HashMap<>();
        for (Map<String, Object> row : orderMapper.selectMaps(new QueryWrapper<ChargeOrder>()
                .select("station_id AS sid",
                        "COUNT(*) AS order_count",
                        "IFNULL(SUM(energy_kwh), 0) AS energy_kwh",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE total_fee END), 0) AS revenue")
                .ge("start_time", todayStart())
                .ne("status", OrderStatus.FAILED.name())
                .in("station_id", ids)
                .groupBy("station_id"))) {
            todayById.put(asLong(row, "sid"), row);
        }
        Map<Long, Long> alertById = new HashMap<>();
        for (Map<String, Object> row : alertMapper.selectMaps(new QueryWrapper<AlertRecord>()
                .select("station_id AS sid", "COUNT(*) AS cnt")
                .ne("status", AlertStatus.RESOLVED.name())
                .in("station_id", ids)
                .groupBy("station_id"))) {
            alertById.put(asLong(row, "sid"), asLong(row, "cnt"));
        }

        List<StationOverviewVo> result = new ArrayList<>();
        for (Station station : stationMapper.selectList(new LambdaQueryWrapper<Station>()
                .in(Station::getId, ids)
                .orderByAsc(Station::getId))) {
            StationOverviewVo vo = new StationOverviewVo();
            vo.setStationId(station.getId());
            vo.setStationName(station.getName());
            vo.setStatus(station.getStatus());
            vo.setCapacityKw(station.getCapacityKw());
            vo.setCurrentPowerKw(ZERO);
            vo.setTodayEnergyKwh(ZERO);
            vo.setTodayRevenue(ZERO);
            StationStats stats = statsById.get(station.getId());
            if (stats != null) {
                vo.setDeviceCount(stats.getDeviceCount());
                vo.setOnlineDeviceCount(stats.getOnlineDeviceCount());
                vo.setPortCount(stats.getPortCount());
                vo.setChargingPortCount(stats.getChargingPortCount());
                vo.setFaultPortCount(stats.getFaultPortCount());
            }
            PowerPointVo power = powerSeriesService.latest(List.of(station.getId()));
            if (power != null) {
                vo.setCurrentPowerKw(power.getPowerKw());
                vo.setPowerSampleAt(power.getTs());
            }
            Map<String, Object> today = todayById.get(station.getId());
            if (today != null) {
                vo.setTodayOrderCount(asLong(today, "order_count"));
                vo.setTodayEnergyKwh(asDecimal(today, "energy_kwh"));
                vo.setTodayRevenue(scale2(asDecimal(today, "revenue")));
            }
            vo.setOpenAlertCount(alertById.getOrDefault(station.getId(), 0L));
            result.add(vo);
        }
        return result;
    }

    /** 首页「分组明细」：按设备分组聚合资产与端口状态，未分组归入「未分组」。 */
    public List<ChargerGroupStats> groupOverview(Long stationId) {
        List<Long> ids = scope(stationId);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return chargerMapper.selectGroupStatsByStationIds(ids);
    }

    /** 全站经营趋势：range 取 7d / 30d / 12m，缺口刻度补零。 */
    public List<BusinessTrendPointVo> businessTrend(Long stationId, String range) {
        List<Long> ids = scope(stationId);
        boolean monthly = "12m".equals(range);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDate today = LocalDate.now();
        LocalDateTime from = monthly
                ? today.withDayOfMonth(1).minusMonths(11).atStartOfDay()
                : today.minusDays(monthly ? 0 : ("30d".equals(range) ? 29 : 6)).atStartOfDay();
        String bucketExpr = monthly ? "DATE_FORMAT(start_time, '%Y-%m')" : "DATE(start_time)";

        Map<String, Map<String, Object>> rowsByKey = new HashMap<>();
        for (Map<String, Object> row : orderMapper.selectMaps(new QueryWrapper<ChargeOrder>()
                .select(bucketExpr + " AS bucket",
                        "COUNT(*) AS order_count",
                        "IFNULL(SUM(energy_kwh), 0) AS energy_kwh",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE total_fee END), 0) AS revenue",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE energy_fee END), 0) AS energy_fee",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE service_fee END), 0) AS service_fee")
                .ge("start_time", from)
                .ne("status", OrderStatus.FAILED.name())
                .in("station_id", ids)
                .groupBy("bucket"))) {
            rowsByKey.put(String.valueOf(asText(row, "bucket")), row);
        }

        List<BusinessTrendPointVo> points = new ArrayList<>();
        int slots = monthly ? 12 : ("30d".equals(range) ? 30 : 7);
        for (int i = slots - 1; i >= 0; i--) {
            String key;
            String label;
            if (monthly) {
                LocalDate month = today.withDayOfMonth(1).minusMonths(i);
                key = String.format("%d-%02d", month.getYear(), month.getMonthValue());
                label = month.getMonthValue() + "月";
            } else {
                LocalDate day = today.minusDays(i);
                key = day.toString();
                label = String.format("%02d-%02d", day.getMonthValue(), day.getDayOfMonth());
            }
            Map<String, Object> row = rowsByKey.getOrDefault(key, Collections.emptyMap());
            points.add(new BusinessTrendPointVo(key, label,
                    asLong(row, "order_count"),
                    asDecimal(row, "energy_kwh"),
                    scale2(asDecimal(row, "revenue")),
                    scale2(asDecimal(row, "energy_fee")),
                    scale2(asDecimal(row, "service_fee"))));
        }
        return points;
    }

    /** 今日电量与营收按下单小时分布，跨小时的订单归入开始小时。 */
    public List<EnergyPointVo> energyTrend(Long stationId) {
        List<Long> ids = scope(stationId);
        List<EnergyPointVo> points = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            points.add(new EnergyPointVo(hour, 0, ZERO, ZERO));
        }
        if (ids.isEmpty()) {
            return points;
        }
        List<Map<String, Object>> rows = orderMapper.selectMaps(new QueryWrapper<ChargeOrder>()
                .select("HOUR(start_time) AS hour_bucket",
                        "COUNT(*) AS order_count",
                        "IFNULL(SUM(energy_kwh), 0) AS energy_kwh",
                        "IFNULL(SUM(total_fee), 0) AS revenue")
                .ge("start_time", todayStart())
                .ne("status", OrderStatus.FAILED.name())
                .in("station_id", ids)
                .groupBy("HOUR(start_time)"));
        for (Map<String, Object> row : rows) {
            int hour = (int) asLong(row, "hour_bucket");
            if (hour < 0 || hour > 23) {
                continue;
            }
            points.set(hour, new EnergyPointVo(hour,
                    asLong(row, "order_count"),
                    asDecimal(row, "energy_kwh"),
                    asDecimal(row, "revenue").setScale(2, RoundingMode.HALF_UP)));
        }
        return points;
    }

    public AlertStatsVo alertStats(Long stationId) {
        List<Long> ids = scope(stationId);
        AlertStatsVo vo = new AlertStatsVo();
        vo.setByType(Collections.emptyList());
        vo.setByLevel(Collections.emptyList());
        if (ids.isEmpty()) {
            return vo;
        }
        vo.setByType(distribution(ids, "alert_type"));
        vo.setByLevel(distribution(ids, "alert_level"));
        vo.setTodayTotal(alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .in(AlertRecord::getStationId, ids)
                .ge(AlertRecord::getOccurredAt, todayStart())));
        vo.setOpenCount(alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .in(AlertRecord::getStationId, ids)
                .eq(AlertRecord::getStatus, AlertStatus.OPEN.name())));
        vo.setAcknowledgedCount(alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .in(AlertRecord::getStationId, ids)
                .eq(AlertRecord::getStatus, AlertStatus.ACKNOWLEDGED.name())));
        return vo;
    }

    /** 实时事件流的历史部分：最近告警与最近订单合并按时间倒序。 */
    public List<DashboardEventVo> events(Long stationId, int limit) {
        List<Long> ids = scope(stationId);
        int size = Math.min(Math.max(limit, 1), EVENT_LIMIT);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<DashboardEventVo> events = new ArrayList<>();
        for (AlertRecord alert : alertMapper.selectList(new LambdaQueryWrapper<AlertRecord>()
                .in(AlertRecord::getStationId, ids)
                .orderByDesc(AlertRecord::getOccurredAt)
                .orderByDesc(AlertRecord::getId)
                .last("LIMIT " + size))) {
            events.add(alertEvent(alert));
        }
        for (ChargeOrder order : orderMapper.selectList(new LambdaQueryWrapper<ChargeOrder>()
                .in(ChargeOrder::getStationId, ids)
                .orderByDesc(ChargeOrder::getId)
                .last("LIMIT " + size))) {
            events.add(orderEvent(order));
        }
        return events.stream()
                .sorted(Comparator.comparing(DashboardEventVo::getTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(size)
                .toList();
    }

    private List<NameCountVo> distribution(List<Long> ids, String column) {
        List<Map<String, Object>> rows = alertMapper.selectMaps(new QueryWrapper<AlertRecord>()
                .select(column + " AS code", "COUNT(*) AS cnt")
                .ge("occurred_at", todayStart())
                .in("station_id", ids)
                .groupBy(column));
        List<NameCountVo> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String code = String.valueOf(asText(row, "code"));
            result.add(new NameCountVo(code, alertLabel(column, code), asLong(row, "cnt")));
        }
        result.sort(Comparator.comparingLong(NameCountVo::getCount).reversed());
        return result;
    }

    private String alertLabel(String column, String code) {
        if ("alert_level".equals(column)) {
            AlertLevel level = AlertLevel.fromText(code);
            return switch (level) {
                case CRITICAL -> "紧急";
                case MAJOR -> "重要";
                case INFO -> "提示";
            };
        }
        AlertType type = AlertType.parse(code);
        return type == null ? code : type.getLabel();
    }

    private DashboardEventVo alertEvent(AlertRecord alert) {
        DashboardEventVo vo = new DashboardEventVo();
        vo.setCategory("ALERT");
        vo.setCode(alert.getAlertType());
        vo.setLevel(alert.getAlertLevel());
        vo.setStatus(alert.getStatus());
        vo.setRefNo(alert.getId() == null ? null : String.valueOf(alert.getId()));
        vo.setStationId(alert.getStationId());
        vo.setDeviceNo(alert.getDeviceNo());
        vo.setPortNo(alert.getPortNo());
        vo.setDetail(alert.getContent());
        vo.setTime(alert.getOccurredAt());
        return vo;
    }

    private DashboardEventVo orderEvent(ChargeOrder order) {
        DashboardEventVo vo = new DashboardEventVo();
        vo.setCategory("ORDER");
        vo.setCode(order.getStatus());
        vo.setStatus(order.getStatus());
        vo.setRefNo(order.getOrderNo());
        vo.setStationId(order.getStationId());
        vo.setPortNo(order.getPortNo());
        Charger charger = order.getChargerId() == null ? null : chargerMapper.selectById(order.getChargerId());
        if (charger != null) {
            vo.setDeviceNo(charger.getDeviceNo());
        }
        vo.setDetail(order.getStopMessage());
        vo.setEnergyKwh(order.getEnergyKwh());
        vo.setTotalFee(order.getTotalFee());
        vo.setTime(order.getEndTime() != null ? order.getEndTime()
                : (order.getStartTime() != null ? order.getStartTime() : order.getCreateTime()));
        return vo;
    }

    /** 请求场站必须在授权范围内；未指定场站时，管理员为全部场站，其他账号为授权场站。 */
    private List<Long> scope(Long stationId) {
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

    private LocalDateTime todayStart() {
        return LocalDate.now().atStartOfDay();
    }

    private Map<String, Object> firstRow(List<Map<String, Object>> rows) {
        return rows == null || rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    private Object value(Map<String, Object> row, String column) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Object direct = row.get(column);
        if (direct != null) {
            return direct;
        }
        String target = column.replace("_", "").toLowerCase();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().replace("_", "").toLowerCase().equals(target)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long asLong(Map<String, Object> row, String column) {
        Object value = value(row, column);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private BigDecimal asDecimal(Map<String, Object> row, String column) {
        Object value = value(row, column);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return value == null ? ZERO : new BigDecimal(String.valueOf(value));
    }

    private String asText(Map<String, Object> row, String column) {
        Object value = value(row, column);
        return value == null ? "" : String.valueOf(value);
    }
}
