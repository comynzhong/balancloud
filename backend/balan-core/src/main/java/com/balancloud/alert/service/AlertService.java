package com.balancloud.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.alert.domain.AlertRecord;
import com.balancloud.alert.dto.AlertQuery;
import com.balancloud.alert.mapper.AlertRecordMapper;
import com.balancloud.alert.model.AlertLevel;
import com.balancloud.alert.model.AlertStatus;
import com.balancloud.alert.model.AlertType;
import com.balancloud.alert.vo.AlertVo;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.ExcelExportSupport;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.system.service.MessageService;
import com.balancloud.workorder.domain.WorkOrder;
import com.balancloud.workorder.mapper.WorkOrderMapper;
import com.balancloud.ws.EventStreamService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final String DEDUP_PREFIX = "alert:dedup:";
    private static final String ACTIVE_PREFIX = "alert:active:";
    private static final String NORMAL_PREFIX = "alert:normal:";
    private static final Duration DEDUP_WINDOW = Duration.ofSeconds(60);
    private static final Duration NORMAL_WINDOW = Duration.ofMinutes(10);
    private static final long RECOVER_AFTER_MS = 60_000;
    private static final String AUTO_ACTOR = "system";
    private static final String PLATFORM_SOURCE = "PLATFORM";
    private static final String DEVICE_SOURCE = "DEVICE";

    private final AlertRecordMapper alertMapper;
    private final ChargerMapper chargerMapper;
    private final StationMapper stationMapper;
    private final StationScopeService scopeService;
    private final StringRedisTemplate redisTemplate;
    private final EventStreamService eventStreamService;
    private final MessageService messageService;
    private final WorkOrderMapper workOrderMapper;

    public AlertService(AlertRecordMapper alertMapper,
                        ChargerMapper chargerMapper,
                        StationMapper stationMapper,
                        StationScopeService scopeService,
                        StringRedisTemplate redisTemplate,
                        EventStreamService eventStreamService,
                        MessageService messageService,
                        WorkOrderMapper workOrderMapper) {
        this.alertMapper = alertMapper;
        this.chargerMapper = chargerMapper;
        this.stationMapper = stationMapper;
        this.scopeService = scopeService;
        this.redisTemplate = redisTemplate;
        this.eventStreamService = eventStreamService;
        this.messageService = messageService;
        this.workOrderMapper = workOrderMapper;
    }

    /**
     * 平台判定告警：阈值超限、设备离线、状态不一致等。
     */
    public AlertRecord raise(Charger charger, Integer portNo, AlertType type, AlertLevel level,
                             String content, BigDecimal metricValue, BigDecimal thresholdValue) {
        return doRaise(charger, portNo, type, level, PLATFORM_SOURCE, content, metricValue, thresholdValue);
    }

    /**
     * 设备主动上报的厂商告警。
     */
    public AlertRecord raiseFromDevice(Charger charger, Integer portNo, AlertType type, AlertLevel level,
                                       String content, BigDecimal metricValue) {
        return doRaise(charger, portNo, type, level, DEVICE_SOURCE, content, metricValue, null);
    }

    private AlertRecord doRaise(Charger charger, Integer portNo, AlertType type, AlertLevel level,
                                String source, String content, BigDecimal metricValue, BigDecimal thresholdValue) {
        if (charger == null || type == null) {
            return null;
        }
        AlertLevel effectiveLevel = AlertLevel.max(level, type.getDefaultLevel());
        if (!dedupAllows(charger.getDeviceNo(), portNo, type)) {
            return null;
        }
        AlertRecord active = findActive(charger.getId(), portNo, type);
        LocalDateTime now = LocalDateTime.now();
        if (active != null) {
            LambdaUpdateWrapper<AlertRecord> merge = new LambdaUpdateWrapper<AlertRecord>()
                    .eq(AlertRecord::getId, active.getId())
                    .set(AlertRecord::getLastOccurredAt, now)
                    .set(AlertRecord::getAlertLevel, effectiveLevel.name());
            if (metricValue != null) {
                merge.set(AlertRecord::getMetricValue, metricValue);
            }
            if (thresholdValue != null) {
                merge.set(AlertRecord::getThresholdValue, thresholdValue);
            }
            alertMapper.update(null, merge);
            markActive(charger.getId(), portNo, type);
            return active;
        }
        AlertRecord alert = new AlertRecord();
        alert.setStationId(charger.getStationId());
        alert.setChargerId(charger.getId());
        alert.setDeviceNo(charger.getDeviceNo());
        alert.setPortNo(portNo);
        alert.setAlertType(type.name());
        alert.setAlertLevel(effectiveLevel.name());
        alert.setStatus(AlertStatus.OPEN.name());
        alert.setSource(source);
        alert.setContent(truncate(content, 500));
        alert.setMetricValue(metricValue);
        alert.setThresholdValue(thresholdValue);
        alert.setOccurredAt(now);
        alert.setLastOccurredAt(now);
        alertMapper.insert(alert);
        markActive(charger.getId(), portNo, type);
        log.info("Alert raised: {} station={} device={} port={} content={}",
                type, charger.getStationId(), charger.getDeviceNo(), portNo, alert.getContent());
        messageService.publish("ALERT", effectiveLevel.name(), charger.getStationId(),
                type.getLabel() + "告警",
                charger.getDeviceNo() + (portNo == null ? "" : " · " + portNo + " 号口")
                        + "：" + (alert.getContent() == null ? "" : alert.getContent()),
                "ALERT", alert.getId());
        push(alert);
        return alert;
    }

    /**
     * 同一租户、同一设备、同一端口、同一类型 60 秒内只处理一条；Redis 不可用时按不去重继续处理，避免漏报。
     */
    private boolean dedupAllows(String deviceNo, Integer portNo, AlertType type) {
        String key = DEDUP_PREFIX + TenantContext.get() + ":" + deviceNo
                + ":" + (portNo == null ? "-" : portNo) + ":" + type.name();
        try {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", DEDUP_WINDOW);
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            log.warn("Alert dedup cache unavailable: {}", e.getMessage());
            return true;
        }
    }

    public AlertRecord findActive(Long chargerId, Integer portNo, AlertType type) {
        return alertMapper.selectOne(activeWrapper(chargerId, portNo, type)
                .orderByDesc(AlertRecord::getId)
                .last("LIMIT 1"));
    }

    private LambdaQueryWrapper<AlertRecord> activeWrapper(Long chargerId, Integer portNo, AlertType type) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getChargerId, chargerId)
                .eq(AlertRecord::getAlertType, type.name())
                .ne(AlertRecord::getStatus, AlertStatus.RESOLVED.name());
        if (portNo == null) {
            wrapper.isNull(AlertRecord::getPortNo);
        } else {
            wrapper.eq(AlertRecord::getPortNo, portNo);
        }
        return wrapper;
    }

    /**
     * 自动恢复：仅对允许自动恢复的类型生效，恢复人记为 system。
     */
    public void autoResolve(Charger charger, Integer portNo, AlertType type) {
        if (charger == null || type == null || !type.isAutoRecoverable()) {
            return;
        }
        AlertRecord active = findActive(charger.getId(), portNo, type);
        if (active == null) {
            return;
        }
        finish(active, AUTO_ACTOR);
        log.info("Alert auto-resolved: {} device={} port={}", type, charger.getDeviceNo(), portNo);
    }

    /**
     * 阈值类告警在同一测点连续 60 秒恢复正常后自动恢复。
     * 未出现活跃标记时直接返回，避免每条测点都查库。
     */
    public void autoResolveIfStable(Charger charger, Integer portNo, AlertType type) {
        try {
            String activeKey = activeKey(charger.getId(), portNo, type);
            if (redisTemplate.opsForValue().get(activeKey) == null) {
                return;
            }
            String normalKey = NORMAL_PREFIX + charger.getId() + ":" + portNo + ":" + type.name();
            String sinceText = redisTemplate.opsForValue().get(normalKey);
            long now = System.currentTimeMillis();
            if (sinceText == null) {
                redisTemplate.opsForValue().set(normalKey, String.valueOf(now), NORMAL_WINDOW);
                return;
            }
            if (now - Long.parseLong(sinceText) < RECOVER_AFTER_MS) {
                return;
            }
            redisTemplate.delete(normalKey);
            AlertRecord active = findActive(charger.getId(), portNo, type);
            if (active == null) {
                redisTemplate.delete(activeKey);
                return;
            }
            autoResolve(charger, portNo, type);
        } catch (Exception e) {
            log.warn("Failed to evaluate auto-recovery for device {} port {} {}: {}",
                    charger.getDeviceNo(), portNo, type, e.getMessage());
        }
    }

    /** 测点仍然超限时，重置连续正常计时。 */
    public void clearNormalStreak(Long chargerId, Integer portNo, AlertType type) {
        try {
            redisTemplate.delete(NORMAL_PREFIX + chargerId + ":" + portNo + ":" + type.name());
        } catch (Exception e) {
            log.warn("Failed to reset normal streak for alert {}:{}", type, e.getMessage());
        }
    }

    private void markActive(Long chargerId, Integer portNo, AlertType type) {
        try {
            redisTemplate.opsForValue().set(activeKey(chargerId, portNo, type), "1");
        } catch (Exception e) {
            log.warn("Failed to mark active alert {}:{}", type, e.getMessage());
        }
    }

    private void clearActive(Long chargerId, Integer portNo, AlertType type) {
        if (chargerId == null || type == null) {
            return;
        }
        try {
            redisTemplate.delete(activeKey(chargerId, portNo, type));
            redisTemplate.delete(NORMAL_PREFIX + chargerId + ":" + portNo + ":" + type.name());
        } catch (Exception e) {
            log.warn("Failed to clear active alert marker {}:{}", type, e.getMessage());
        }
    }

    private String activeKey(Long chargerId, Integer portNo, AlertType type) {
        return ACTIVE_PREFIX + chargerId + ":" + (portNo == null ? "-" : portNo) + ":" + type.name();
    }

    public AlertVo ack(Long id) {
        AlertRecord alert = requireAlert(id);
        if (AlertStatus.RESOLVED.name().equals(alert.getStatus())) {
            throw new ServiceException(400, "告警已恢复，无需确认");
        }
        if (AlertStatus.ACKNOWLEDGED.name().equals(alert.getStatus())) {
            return toVo(alert);
        }
        String operator = actor("operator");
        alertMapper.update(null, new LambdaUpdateWrapper<AlertRecord>()
                .eq(AlertRecord::getId, id)
                .eq(AlertRecord::getStatus, AlertStatus.OPEN.name())
                .set(AlertRecord::getStatus, AlertStatus.ACKNOWLEDGED.name())
                .set(AlertRecord::getAcknowledgedBy, operator)
                .set(AlertRecord::getAcknowledgedAt, LocalDateTime.now()));
        AlertRecord updated = alertMapper.selectById(id);
        push(updated);
        return toVo(updated);
    }

    public AlertVo resolve(Long id) {
        AlertRecord alert = requireAlert(id);
        if (AlertStatus.RESOLVED.name().equals(alert.getStatus())) {
            return toVo(alert);
        }
        finish(alert, actor(AUTO_ACTOR));
        return toVo(alertMapper.selectById(id));
    }

    private void finish(AlertRecord alert, String operator) {
        alertMapper.update(null, new LambdaUpdateWrapper<AlertRecord>()
                .eq(AlertRecord::getId, alert.getId())
                .ne(AlertRecord::getStatus, AlertStatus.RESOLVED.name())
                .set(AlertRecord::getStatus, AlertStatus.RESOLVED.name())
                .set(AlertRecord::getResolvedBy, operator)
                .set(AlertRecord::getResolvedAt, LocalDateTime.now()));
        AlertRecord updated = alertMapper.selectById(alert.getId());
        if (updated != null) {
            clearActive(updated.getChargerId(), updated.getPortNo(), AlertType.parse(updated.getAlertType()));
        }
        push(updated);
    }

    public TableDataInfo list(AlertQuery query) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        LambdaQueryWrapper<AlertRecord> wrapper = buildWrapper(query);
        if (wrapper == null) {
            return TableDataInfo.of(0, Collections.emptyList());
        }
        Page<AlertRecord> page = alertMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return TableDataInfo.of(page.getTotal(), toVos(page.getRecords()));
    }

    public AlertVo detail(Long id) {
        return toVo(requireAlert(id));
    }

    public List<AlertVo> recent(AlertQuery query, int limit) {
        LambdaQueryWrapper<AlertRecord> wrapper = buildWrapper(query);
        if (wrapper == null) {
            return Collections.emptyList();
        }
        return toVos(alertMapper.selectList(wrapper.last("LIMIT " + Math.min(Math.max(limit, 1), 100))));
    }

    public void export(AlertQuery query, HttpServletResponse response) {
        LambdaQueryWrapper<AlertRecord> wrapper = buildWrapper(query);
        List<AlertVo> rows = wrapper == null
                ? Collections.emptyList()
                : toVos(alertMapper.selectList(wrapper.last("LIMIT 10000")));
        XSSFWorkbook workbook = ExcelExportSupport.workbook();
        String[] headers = {"告警ID", "场站", "设备编号", "设备名称", "端口", "告警类型", "级别", "处理状态",
                "来源", "告警内容", "指标值", "阈值", "首次发生", "最近发生", "确认人", "确认时间", "恢复人", "恢复时间"};
        int[] widths = {8, 18, 16, 16, 8, 12, 10, 12, 12, 34, 12, 12, 20, 20, 12, 20, 12, 20};
        Sheet sheet = ExcelExportSupport.sheet(workbook, "告警记录", headers, widths);
        CellStyle moneyStyle = ExcelExportSupport.moneyStyle(workbook);
        int index = 1;
        for (AlertVo vo : rows) {
            Row row = sheet.createRow(index++);
            int col = 0;
            row.createCell(col++).setCellValue(vo.getId() == null ? 0 : vo.getId());
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getStationName()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getDeviceNo()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getDeviceName()));
            row.createCell(col++).setCellValue(vo.getPortNo() == null ? "" : String.valueOf(vo.getPortNo()));
            row.createCell(col++).setCellValue(alertTypeLabel(vo.getAlertType()));
            row.createCell(col++).setCellValue(alertLevelLabel(vo.getAlertLevel()));
            row.createCell(col++).setCellValue(alertStatusLabel(vo.getStatus()));
            row.createCell(col++).setCellValue(sourceLabel(vo.getSource()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getContent()));
            ExcelExportSupport.money(row, col++, vo.getMetricValue(), moneyStyle);
            ExcelExportSupport.money(row, col++, vo.getThresholdValue(), moneyStyle);
            row.createCell(col++).setCellValue(ExcelExportSupport.time(vo.getOccurredAt()));
            row.createCell(col++).setCellValue(ExcelExportSupport.time(vo.getLastOccurredAt()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getAcknowledgedBy()));
            row.createCell(col++).setCellValue(ExcelExportSupport.time(vo.getAcknowledgedAt()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getResolvedBy()));
            row.createCell(col).setCellValue(ExcelExportSupport.time(vo.getResolvedAt()));
        }
        ExcelExportSupport.finish(workbook, sheet, index - 1, headers.length, response, "alerts");
    }

    private String alertTypeLabel(String type) {
        AlertType parsed = AlertType.parse(type);
        return parsed == null ? ExcelExportSupport.text(type) : parsed.getLabel();
    }

    private String alertLevelLabel(String level) {
        AlertLevel parsed = level == null ? null : AlertLevel.fromText(level);
        if (parsed == null) {
            return ExcelExportSupport.text(level);
        }
        return switch (parsed) {
            case CRITICAL -> "严重";
            case MAJOR -> "重要";
            case INFO -> "提示";
        };
    }

    private String alertStatusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "OPEN" -> "未处理";
            case "ACKNOWLEDGED" -> "已确认";
            case "RESOLVED" -> "已恢复";
            default -> status;
        };
    }

    private String sourceLabel(String source) {
        if (source == null) {
            return "";
        }
        return "DEVICE".equals(source) ? "设备上报" : "平台判定";
    }

    private LambdaQueryWrapper<AlertRecord> buildWrapper(AlertQuery query) {
        List<Long> scope = scopeService.authorizedStationIds();
        if (query.getStationId() != null) {
            scopeService.assertAccess(query.getStationId());
        }
        if (scope != null && scope.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        if (scope != null) {
            wrapper.in(AlertRecord::getStationId, scope);
        }
        if (query.getStationId() != null) {
            wrapper.eq(AlertRecord::getStationId, query.getStationId());
        }
        if (query.getChargerId() != null) {
            wrapper.eq(AlertRecord::getChargerId, query.getChargerId());
        }
        if (query.getPortNo() != null) {
            wrapper.eq(AlertRecord::getPortNo, query.getPortNo());
        }
        if (StringUtils.hasText(query.getAlertType())) {
            wrapper.eq(AlertRecord::getAlertType, query.getAlertType().trim().toUpperCase());
        }
        if (StringUtils.hasText(query.getAlertLevel())) {
            wrapper.eq(AlertRecord::getAlertLevel, query.getAlertLevel().trim().toUpperCase());
        }
        if (StringUtils.hasText(query.getStatus())) {
            String status = query.getStatus().trim().toUpperCase();
            if ("ACTIVE".equals(status)) {
                wrapper.in(AlertRecord::getStatus, AlertStatus.OPEN.name(), AlertStatus.ACKNOWLEDGED.name());
            } else {
                wrapper.eq(AlertRecord::getStatus, status);
            }
        }
        if (query.getBeginTime() != null) {
            wrapper.ge(AlertRecord::getOccurredAt, query.getBeginTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(AlertRecord::getOccurredAt, query.getEndTime());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(AlertRecord::getDeviceNo, keyword).or().like(AlertRecord::getContent, keyword));
        }
        wrapper.orderByDesc(AlertRecord::getOccurredAt).orderByDesc(AlertRecord::getId);
        return wrapper;
    }

    private AlertRecord requireAlert(Long id) {
        AlertRecord alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new ServiceException(404, "告警不存在");
        }
        scopeService.assertAccess(alert.getStationId());
        return alert;
    }

    private List<AlertVo> toVos(List<AlertRecord> alerts) {
        if (alerts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Charger> chargers = loadChargers(alerts);
        Map<Long, Station> stations = loadStations(alerts);
        Map<Long, WorkOrder> workOrders = loadWorkOrders(alerts);
        return alerts.stream().map(alert -> {
            AlertVo vo = basicVo(alert);
            Charger charger = chargers.get(alert.getChargerId());
            if (charger != null) {
                vo.setDeviceNo(charger.getDeviceNo());
                vo.setDeviceName(charger.getName());
            }
            Station station = stations.get(alert.getStationId());
            if (station != null) {
                vo.setStationName(station.getName());
            }
            applyWorkOrder(vo, workOrders.get(alert.getId()));
            return vo;
        }).toList();
    }

    private Map<Long, WorkOrder> loadWorkOrders(List<AlertRecord> alerts) {
        List<Long> ids = alerts.stream().map(AlertRecord::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                        .in(WorkOrder::getAlertId, ids)
                        .orderByAsc(WorkOrder::getId))
                .stream()
                .collect(Collectors.toMap(WorkOrder::getAlertId, item -> item, (first, second) -> second));
    }

    private void applyWorkOrder(AlertVo vo, WorkOrder order) {
        if (vo == null || order == null) {
            return;
        }
        vo.setWorkOrderId(order.getId());
        vo.setWorkOrderNo(order.getWorkOrderNo());
        vo.setWorkOrderStatus(order.getStatus());
    }

    private Map<Long, Charger> loadChargers(List<AlertRecord> alerts) {
        List<Long> ids = alerts.stream().map(AlertRecord::getChargerId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return chargerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Charger::getId, item -> item));
    }

    private Map<Long, Station> loadStations(List<AlertRecord> alerts) {
        List<Long> ids = alerts.stream().map(AlertRecord::getStationId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return stationMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Station::getId, item -> item));
    }

    private AlertVo toVo(AlertRecord alert) {
        if (alert == null) {
            return null;
        }
        AlertVo vo = basicVo(alert);
        if (alert.getChargerId() != null) {
            Charger charger = chargerMapper.selectById(alert.getChargerId());
            if (charger != null) {
                vo.setDeviceNo(charger.getDeviceNo());
                vo.setDeviceName(charger.getName());
            }
        }
        if (alert.getStationId() != null) {
            Station station = stationMapper.selectById(alert.getStationId());
            if (station != null) {
                vo.setStationName(station.getName());
            }
        }
        applyWorkOrder(vo, workOrderMapper.selectOne(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getAlertId, alert.getId())
                .orderByDesc(WorkOrder::getId)
                .last("LIMIT 1")));
        return vo;
    }

    private AlertVo basicVo(AlertRecord alert) {
        AlertVo vo = new AlertVo();
        BeanUtils.copyProperties(alert, vo);
        return vo;
    }

    private void push(AlertRecord alert) {
        if (alert == null) {
            return;
        }
        eventStreamService.publishAfterCommit("alert", alert.getStationId(), toVo(alert));
    }

    private String actor(String fallback) {
        String username = SecurityUtils.getUsername();
        return StringUtils.hasText(username) ? username : fallback;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
