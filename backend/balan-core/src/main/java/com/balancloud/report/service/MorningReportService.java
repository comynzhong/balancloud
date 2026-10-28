package com.balancloud.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.balancloud.alert.domain.AlertRecord;
import com.balancloud.alert.mapper.AlertRecordMapper;
import com.balancloud.app.domain.AppUser;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.device.vo.StationStats;
import com.balancloud.framework.cluster.ClusterLockService;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.framework.tenant.TenantTaskExecutor;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.report.vo.MorningReportVo;
import com.balancloud.system.domain.SysMessage;
import com.balancloud.system.mapper.SysMessageMapper;
import com.balancloud.system.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 租户运营晨报：昨日经营、用户、告警与设备状态，可页面查看，也可每日 08:00 推一条站内信。
 *
 * <p>口径与首页看板一致：失败单不计入营收，退款单计入单量与电量但不计营收。
 * 页面按当前账号的场站授权收敛；定时推送是租户级口径，两者共用同一套聚合方法。
 */
@Service
public class MorningReportService {

    private static final Logger log = LoggerFactory.getLogger(MorningReportService.class);
    private static final String BIZ_TYPE = "MORNING_REPORT";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("M月d日");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ChargeOrderMapper orderMapper;
    private final AlertRecordMapper alertMapper;
    private final AppUserMapper appUserMapper;
    private final ChargerMapper chargerMapper;
    private final StationMapper stationMapper;
    private final StationScopeService scopeService;
    private final MessageService messageService;
    private final SysMessageMapper messageMapper;
    private final ClusterLockService clusterLock;
    private final TenantTaskExecutor tenantTasks;

    public MorningReportService(ChargeOrderMapper orderMapper,
                                AlertRecordMapper alertMapper,
                                AppUserMapper appUserMapper,
                                ChargerMapper chargerMapper,
                                StationMapper stationMapper,
                                StationScopeService scopeService,
                                MessageService messageService,
                                SysMessageMapper messageMapper,
                                ClusterLockService clusterLock,
                                TenantTaskExecutor tenantTasks) {
        this.orderMapper = orderMapper;
        this.alertMapper = alertMapper;
        this.appUserMapper = appUserMapper;
        this.chargerMapper = chargerMapper;
        this.stationMapper = stationMapper;
        this.scopeService = scopeService;
        this.messageService = messageService;
        this.messageMapper = messageMapper;
        this.clusterLock = clusterLock;
        this.tenantTasks = tenantTasks;
    }

    /** 页面口径：按当前账号的场站授权过滤，管理员为全租户。 */
    public MorningReportVo report(LocalDate date) {
        LocalDate target = resolve(date);
        return build(target, scopeService.authorizedStationIds());
    }

    public Map<String, Object> push(LocalDate date) {
        LocalDate target = resolve(date);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", target);

        Long tenantId = TenantContext.get();
        // 窗口键带上日期：不然补推前一天的晨报会把后面 5 分钟里所有日期的手动推送一起挡掉。
        // 租户段留在最后，集群监控的锁盘点才按「纯数字后缀=租户号」拆得出来。
        if (!clusterLock.acquireWindow("morning-report-push:" + target + ":" + tenantId, Duration.ofMinutes(5))) {
            result.put("pushed", false);
            result.put("reason", "其他实例正在推送");
            return result;
        }
        if (alreadyPushed(target)) {
            result.put("pushed", false);
            result.put("reason", "该日期已推送过");
            return result;
        }

        MorningReportVo report = build(target, null);
        String title = title(report);
        messageService.publish("SYSTEM", "INFO", null, title, content(report), BIZ_TYPE, target.toString());
        result.put("pushed", true);
        result.put("reason", null);
        result.put("title", title);
        log.info("Morning report {} pushed for tenant {}", target, tenantId);
        return result;
    }

    /**
     * 每日 08:00（北京时间）按租户推送。窗口键 23 小时保证多实例只推一次；
     * 真正的重复保护还有站内信 bizType+bizId 检查，窗口锁失效时也不会重复。
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Shanghai", scheduler = "reportTaskScheduler")
    public void pushDaily() {
        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);
        tenantTasks.forEach("morning-report", Duration.ofDays(1), Duration.ofHours(23), () -> {
            Map<String, Object> result = push(yesterday);
            if (!Boolean.TRUE.equals(result.get("pushed"))) {
                log.info("Morning report {} skipped: {}", yesterday, result.get("reason"));
            }
        });
    }

    private MorningReportVo build(LocalDate date, List<Long> scopedStationIds) {
        MorningReportVo report = new MorningReportVo();
        report.setDate(date);
        report.setGeneratedAt(LocalDateTime.now());
        report.setEnergyKwh(ZERO);
        report.setRevenue(ZERO);
        report.setEnergyFee(ZERO);
        report.setServiceFee(ZERO);
        report.setTimeFee(ZERO);
        report.setRefundAmount(ZERO);
        report.setTopStations(List.of());
        report.setGrowth(emptyGrowth());

        List<Long> stationIds = scopedStationIds == null ? allStationIds() : scopedStationIds;
        if (stationIds.isEmpty()) {
            report.setHeadline("暂无授权场站，无法生成" + date.format(DATE_LABEL) + "的运营晨报。");
            return report;
        }

        LocalDateTime start = date.atStartOfDay(ZONE).toLocalDateTime();
        LocalDateTime end = start.plusDays(1);
        Map<String, Object> orders = orderAggregate(stationIds, start, end);
        report.setOrderCount(asLong(orders, "order_count"));
        report.setCompletedCount(asLong(orders, "completed_count"));
        report.setAbnormalCount(asLong(orders, "abnormal_count"));
        report.setFailedCount(asLong(orders, "failed_count"));
        report.setUnpaidCount(asLong(orders, "unpaid_count"));
        report.setEnergyKwh(asDecimal(orders, "energy_kwh"));
        report.setRevenue(scale2(asDecimal(orders, "revenue")));
        report.setEnergyFee(scale2(asDecimal(orders, "energy_fee")));
        report.setServiceFee(scale2(asDecimal(orders, "service_fee")));
        report.setTimeFee(scale2(asDecimal(orders, "time_fee")));
        report.setRefundCount(asLong(orders, "refund_count"));
        report.setRefundAmount(scale2(asDecimal(orders, "refund_amount")));

        Map<String, Object> previous = orderAggregate(stationIds, start.minusDays(1), start);
        MorningReportVo.Growth growth = new MorningReportVo.Growth();
        growth.setOrderPercent(growthPercent(
                BigDecimal.valueOf(report.getOrderCount()),
                BigDecimal.valueOf(asLong(previous, "order_count"))));
        growth.setEnergyPercent(growthPercent(
                report.getEnergyKwh(), asDecimal(previous, "energy_kwh")));
        growth.setRevenuePercent(growthPercent(
                report.getRevenue(), scale2(asDecimal(previous, "revenue"))));
        report.setGrowth(growth);

        report.setNewUserCount(appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .ge(AppUser::getCreateTime, start)
                .lt(AppUser::getCreateTime, end)));
        report.setActiveUserCount(asLong(firstRow(orderMapper.selectMaps(new QueryWrapper<ChargeOrder>()
                .select("COUNT(DISTINCT app_user_id) AS cnt")
                .ge("create_time", start)
                .lt("create_time", end)
                .isNotNull("app_user_id")
                .in("station_id", stationIds))), "cnt"));

        Map<String, Object> alerts = firstRow(alertMapper.selectMaps(new QueryWrapper<AlertRecord>()
                .select("COUNT(*) AS alert_count",
                        "SUM(CASE WHEN alert_level = 'CRITICAL' THEN 1 ELSE 0 END) AS critical_count",
                        "SUM(CASE WHEN alert_level = 'MAJOR' THEN 1 ELSE 0 END) AS major_count",
                        "SUM(CASE WHEN status <> 'RESOLVED' THEN 1 ELSE 0 END) AS unresolved_count")
                .ge("occurred_at", start)
                .lt("occurred_at", end)
                .in("station_id", stationIds)));
        report.setAlertCount(asLong(alerts, "alert_count"));
        report.setCriticalAlertCount(asLong(alerts, "critical_count"));
        report.setMajorAlertCount(asLong(alerts, "major_count"));
        report.setUnresolvedAlertCount(asLong(alerts, "unresolved_count"));

        long deviceCount = 0;
        long onlineCount = 0;
        long portCount = 0;
        long faultCount = 0;
        for (StationStats stats : chargerMapper.selectStatsByStationIds(stationIds)) {
            deviceCount += stats.getDeviceCount();
            onlineCount += stats.getOnlineDeviceCount();
            portCount += stats.getPortCount();
            faultCount += stats.getFaultPortCount();
        }
        report.setDeviceCount(deviceCount);
        report.setOnlineDeviceCount(onlineCount);
        report.setOfflineDeviceCount(Math.max(deviceCount - onlineCount, 0));
        report.setPortCount(portCount);
        report.setFaultPortCount(faultCount);

        report.setTopStations(topStations(stationIds, start, end));
        report.setHeadline(headline(report));
        return report;
    }

    private Map<String, Object> orderAggregate(List<Long> stationIds, LocalDateTime from, LocalDateTime to) {
        return firstRow(orderMapper.selectMaps(new QueryWrapper<ChargeOrder>()
                .select("COUNT(*) AS order_count",
                        "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count",
                        "SUM(CASE WHEN status = 'ABNORMAL' THEN 1 ELSE 0 END) AS abnormal_count",
                        "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count",
                        "SUM(CASE WHEN payment_status = 'UNPAID' AND status IN ('COMPLETED', 'ABNORMAL') "
                                + "THEN 1 ELSE 0 END) AS unpaid_count",
                        "IFNULL(SUM(energy_kwh), 0) AS energy_kwh",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE total_fee END), 0) AS revenue",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE energy_fee END), 0) AS energy_fee",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE service_fee END), 0) AS service_fee",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE time_fee END), 0) AS time_fee",
                        "SUM(CASE WHEN payment_status = 'REFUNDED' THEN 1 ELSE 0 END) AS refund_count",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN total_fee ELSE 0 END), 0) AS refund_amount")
                .ge("create_time", from)
                .lt("create_time", to)
                .in("station_id", stationIds)));
    }

    private List<MorningReportVo.StationLine> topStations(List<Long> stationIds,
                                                          LocalDateTime from, LocalDateTime to) {
        List<Map<String, Object>> rows = orderMapper.selectMaps(new QueryWrapper<ChargeOrder>()
                .select("station_id AS sid",
                        "COUNT(*) AS order_count",
                        "IFNULL(SUM(energy_kwh), 0) AS energy_kwh",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN 0 ELSE total_fee END), 0) AS revenue")
                .ge("create_time", from)
                .lt("create_time", to)
                .ne("status", "FAILED")
                .in("station_id", stationIds)
                .groupBy("station_id")
                .orderByDesc("revenue")
                .last("LIMIT 5"));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(row -> asLong(row, "sid")).toList();
        Map<Long, String> names = stationMapper.selectList(new LambdaQueryWrapper<Station>()
                        .select(Station::getId, Station::getName)
                        .in(Station::getId, ids))
                .stream().collect(Collectors.toMap(Station::getId, Station::getName, (a, b) -> a));
        List<MorningReportVo.StationLine> lines = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long stationId = asLong(row, "sid");
            MorningReportVo.StationLine line = new MorningReportVo.StationLine();
            line.setStationId(stationId);
            line.setStationName(names.getOrDefault(stationId, "场站 #" + stationId));
            line.setOrderCount(asLong(row, "order_count"));
            line.setEnergyKwh(asDecimal(row, "energy_kwh"));
            line.setRevenue(scale2(asDecimal(row, "revenue")));
            lines.add(line);
        }
        return lines;
    }

    static String title(MorningReportVo report) {
        return "运营晨报 · " + report.getDate().format(DATE_LABEL)
                + "：营收 ¥" + money(report.getRevenue())
                + "，订单 " + report.getOrderCount() + " 笔";
    }

    static String content(MorningReportVo report) {
        String top = report.getTopStations().isEmpty() ? "无"
                : report.getTopStations().stream().limit(3)
                .map(line -> line.getStationName() + " ¥" + money(line.getRevenue()))
                .collect(Collectors.joining(" / "));
        return "订单 " + report.getOrderCount() + " 笔（完成 " + report.getCompletedCount()
                + " / 异常 " + report.getAbnormalCount() + " / 失败 " + report.getFailedCount()
                + "），充电 " + energy(report.getEnergyKwh()) + " kWh，营收 ¥" + money(report.getRevenue()) + "。\n"
                + "电费 ¥" + money(report.getEnergyFee()) + " · 服务费 ¥" + money(report.getServiceFee())
                + " · 退款 " + report.getRefundCount() + " 笔 ¥" + money(report.getRefundAmount()) + "。\n"
                + "新增用户 " + report.getNewUserCount() + " 人，活跃用户 " + report.getActiveUserCount() + " 人。\n"
                + "告警 " + report.getAlertCount() + " 条（紧急 " + report.getCriticalAlertCount()
                + " / 重要 " + report.getMajorAlertCount() + " / 未闭环 " + report.getUnresolvedAlertCount() + "）。\n"
                + "设备 " + report.getDeviceCount() + " 台，在线 " + report.getOnlineDeviceCount()
                + " 台，故障端口 " + report.getFaultPortCount() + " 口。\n"
                + "Top 场站：" + top;
    }

    private static String headline(MorningReportVo report) {
        if (report.getOrderCount() == 0 && report.getAlertCount() == 0) {
            return "昨日暂无订单与告警，可检查设备在线状态或授权场站范围。";
        }
        return "昨日 " + report.getOrderCount() + " 笔订单完成 " + report.getCompletedCount()
                + " 笔，充电 " + energy(report.getEnergyKwh()) + " 度，营收 ¥" + money(report.getRevenue())
                + "；新增用户 " + report.getNewUserCount() + " 人，告警 " + report.getAlertCount()
                + " 条（未闭环 " + report.getUnresolvedAlertCount() + "）。";
    }

    private boolean alreadyPushed(LocalDate date) {
        return messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getBizType, BIZ_TYPE)
                .eq(SysMessage::getBizId, date.toString())) > 0;
    }

    private List<Long> allStationIds() {
        return stationMapper.selectList(new LambdaQueryWrapper<Station>().select(Station::getId))
                .stream().map(Station::getId).toList();
    }

    private LocalDate resolve(LocalDate date) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate target = date == null ? today.minusDays(1) : date;
        if (target.isAfter(today)) {
            throw new ServiceException(400, "不能生成未来日期的晨报");
        }
        return target;
    }

    private MorningReportVo.Growth emptyGrowth() {
        MorningReportVo.Growth growth = new MorningReportVo.Growth();
        growth.setOrderPercent(null);
        growth.setEnergyPercent(null);
        growth.setRevenuePercent(null);
        return growth;
    }

    /** 前一日为 0 时不报增长率，前端显示「—」，避免把无基数写成 +100%。 */
    static BigDecimal growthPercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(ZERO) <= 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String energy(BigDecimal value) {
        return value == null ? "0" : value.setScale(1, RoundingMode.HALF_UP).toPlainString();
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
}
