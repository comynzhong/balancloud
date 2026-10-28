package com.balancloud.workorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.alert.domain.AlertRecord;
import com.balancloud.alert.mapper.AlertRecordMapper;
import com.balancloud.alert.model.AlertLevel;
import com.balancloud.alert.model.AlertType;
import com.balancloud.alert.service.AlertService;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.system.domain.SysUser;
import com.balancloud.system.mapper.SysUserMapper;
import com.balancloud.workorder.domain.WorkOrder;
import com.balancloud.workorder.domain.WorkOrderRecord;
import com.balancloud.workorder.dto.WorkOrderAssignRequest;
import com.balancloud.workorder.dto.WorkOrderCloseRequest;
import com.balancloud.workorder.dto.WorkOrderCreateRequest;
import com.balancloud.workorder.dto.WorkOrderFromAlertRequest;
import com.balancloud.workorder.dto.WorkOrderQuery;
import com.balancloud.workorder.dto.WorkOrderRecordRequest;
import com.balancloud.workorder.dto.WorkOrderResolveRequest;
import com.balancloud.workorder.dto.WorkOrderUpdateRequest;
import com.balancloud.workorder.mapper.WorkOrderMapper;
import com.balancloud.workorder.mapper.WorkOrderRecordMapper;
import com.balancloud.workorder.model.WorkOrderPriority;
import com.balancloud.workorder.model.WorkOrderStatus;
import com.balancloud.workorder.vo.WorkOrderAssigneeVo;
import com.balancloud.workorder.vo.WorkOrderDetailVo;
import com.balancloud.workorder.vo.WorkOrderRecordVo;
import com.balancloud.workorder.vo.WorkOrderStatsVo;
import com.balancloud.workorder.vo.WorkOrderVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 运维工单：告警一键生成、指派、处理记录、解决与归档。
 */
@Service
public class WorkOrderService {

    public static final String SOURCE_ALERT = "ALERT";
    public static final String SOURCE_MANUAL = "MANUAL";

    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderRecordMapper recordMapper;
    private final AlertRecordMapper alertMapper;
    private final ChargerMapper chargerMapper;
    private final StationMapper stationMapper;
    private final SysUserMapper userMapper;
    private final StationScopeService scopeService;
    private final AlertService alertService;

    public WorkOrderService(WorkOrderMapper workOrderMapper,
                            WorkOrderRecordMapper recordMapper,
                            AlertRecordMapper alertMapper,
                            ChargerMapper chargerMapper,
                            StationMapper stationMapper,
                            SysUserMapper userMapper,
                            StationScopeService scopeService,
                            AlertService alertService) {
        this.workOrderMapper = workOrderMapper;
        this.recordMapper = recordMapper;
        this.alertMapper = alertMapper;
        this.chargerMapper = chargerMapper;
        this.stationMapper = stationMapper;
        this.userMapper = userMapper;
        this.scopeService = scopeService;
        this.alertService = alertService;
    }

    public TableDataInfo list(WorkOrderQuery query) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        List<Long> scope = scopeService.authorizedStationIds();
        if (query.getStationId() != null) {
            scopeService.assertAccess(query.getStationId());
        }
        if (scope != null && scope.isEmpty()) {
            return TableDataInfo.of(0, Collections.emptyList());
        }
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (scope != null) {
            wrapper.in(WorkOrder::getStationId, scope);
        }
        if (query.getStationId() != null) {
            wrapper.eq(WorkOrder::getStationId, query.getStationId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            String status = query.getStatus().trim().toUpperCase();
            if ("ACTIVE".equals(status)) {
                wrapper.in(WorkOrder::getStatus, WorkOrderStatus.OPEN.name(), WorkOrderStatus.PROCESSING.name());
            } else {
                WorkOrderStatus parsed = WorkOrderStatus.parse(status);
                if (parsed == null) {
                    throw new ServiceException(400, "工单状态不合法");
                }
                wrapper.eq(WorkOrder::getStatus, parsed.name());
            }
        }
        if (StringUtils.hasText(query.getPriority())) {
            WorkOrderPriority priority = WorkOrderPriority.parse(query.getPriority());
            if (priority == null) {
                throw new ServiceException(400, "优先级不合法");
            }
            wrapper.eq(WorkOrder::getPriority, priority.name());
        }
        if (StringUtils.hasText(query.getSource())) {
            wrapper.eq(WorkOrder::getSource, query.getSource().trim().toUpperCase());
        }
        if (Boolean.TRUE.equals(query.getMine())) {
            wrapper.eq(WorkOrder::getAssigneeId, SecurityUtils.getUserId());
        } else if (query.getAssigneeId() != null) {
            wrapper.eq(WorkOrder::getAssigneeId, query.getAssigneeId());
        }
        if (query.getBeginTime() != null) {
            wrapper.ge(WorkOrder::getCreateTime, query.getBeginTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(WorkOrder::getCreateTime, query.getEndTime());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(WorkOrder::getWorkOrderNo, keyword)
                    .or().like(WorkOrder::getTitle, keyword)
                    .or().like(WorkOrder::getDeviceNo, keyword)
                    .or().like(WorkOrder::getAssigneeName, keyword));
        }
        wrapper.last("ORDER BY FIELD(status, 'PROCESSING', 'OPEN', 'RESOLVED', 'CLOSED'), "
                + "FIELD(priority, 'URGENT', 'HIGH', 'NORMAL', 'LOW'), id DESC");
        Page<WorkOrder> page = workOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return TableDataInfo.of(page.getTotal(), toVos(page.getRecords()));
    }

    public WorkOrderDetailVo detail(Long id) {
        WorkOrder order = require(id);
        WorkOrderDetailVo detail = new WorkOrderDetailVo();
        detail.setWorkOrder(toVos(List.of(order)).get(0));
        detail.setRecords(recordMapper.selectList(new LambdaQueryWrapper<WorkOrderRecord>()
                        .eq(WorkOrderRecord::getWorkOrderId, id)
                        .orderByAsc(WorkOrderRecord::getId))
                .stream().map(this::toRecordVo).toList());
        return detail;
    }

    public WorkOrderStatsVo stats() {
        List<Long> scope = scopeService.authorizedStationIds();
        WorkOrderStatsVo stats = new WorkOrderStatsVo();
        if (scope != null && scope.isEmpty()) {
            return stats;
        }
        stats.setOpen(countByStatus(scope, WorkOrderStatus.OPEN));
        stats.setProcessing(countByStatus(scope, WorkOrderStatus.PROCESSING));
        stats.setResolved(countByStatus(scope, WorkOrderStatus.RESOLVED));
        stats.setClosed(countByStatus(scope, WorkOrderStatus.CLOSED));
        stats.setTotal(stats.getOpen() + stats.getProcessing() + stats.getResolved() + stats.getClosed());
        stats.setActive(stats.getOpen() + stats.getProcessing());
        return stats;
    }

    public List<WorkOrderAssigneeVo> assignees() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getUserId, SysUser::getUserName, SysUser::getNickName)
                        .eq(SysUser::getStatus, "0")
                        .orderByAsc(SysUser::getUserId))
                .stream()
                .map(user -> {
                    WorkOrderAssigneeVo vo = new WorkOrderAssigneeVo();
                    vo.setUserId(user.getUserId());
                    vo.setUserName(user.getUserName());
                    vo.setNickName(user.getNickName());
                    return vo;
                })
                .toList();
    }

    @Transactional
    public WorkOrderVo create(WorkOrderCreateRequest request) {
        scopeService.assertAccess(request.getStationId());
        String title = requireTitle(request.getTitle());
        String priority = normalizePriority(request.getPriority(), WorkOrderPriority.NORMAL.name());
        Charger charger = null;
        if (request.getChargerId() != null) {
            charger = chargerMapper.selectById(request.getChargerId());
            if (charger == null) {
                throw new ServiceException(404, "设备不存在");
            }
            if (!Objects.equals(charger.getStationId(), request.getStationId())) {
                throw new ServiceException(400, "所选设备不属于该场站");
            }
        }
        checkPortNo(charger, request.getPortNo());
        SysUser assignee = request.getAssigneeId() == null ? null : requireActiveUser(request.getAssigneeId());

        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo(nextNo());
        order.setTitle(title);
        order.setDescription(trimToNull(request.getDescription()));
        order.setSource(SOURCE_MANUAL);
        order.setStationId(request.getStationId());
        order.setChargerId(charger == null ? null : charger.getId());
        order.setDeviceNo(charger == null ? null : charger.getDeviceNo());
        order.setPortNo(request.getPortNo());
        order.setPriority(priority);
        order.setStatus(assignee == null ? WorkOrderStatus.OPEN.name() : WorkOrderStatus.PROCESSING.name());
        order.setAssigneeId(assignee == null ? null : assignee.getUserId());
        order.setAssigneeName(assignee == null ? null : displayName(assignee));
        order.setCreator(currentUser());
        workOrderMapper.insert(order);

        writeRecord(order.getId(), "CREATE", "创建工单：" + title, currentUser());
        if (assignee != null) {
            writeRecord(order.getId(), "ASSIGN", "指派给 " + displayName(assignee), currentUser());
        }
        return toVos(List.of(require(order.getId()))).get(0);
    }

    @Transactional
    public WorkOrderVo createFromAlert(Long alertId, WorkOrderFromAlertRequest request) {
        AlertRecord alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new ServiceException(404, "告警不存在");
        }
        scopeService.assertAccess(alert.getStationId());
        WorkOrder existing = workOrderMapper.selectOne(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getAlertId, alertId)
                .in(WorkOrder::getStatus, WorkOrderStatus.OPEN.name(), WorkOrderStatus.PROCESSING.name())
                .orderByDesc(WorkOrder::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new ServiceException(409, "该告警已有进行中的工单 " + existing.getWorkOrderNo());
        }
        WorkOrderFromAlertRequest payload = request == null ? new WorkOrderFromAlertRequest() : request;
        String title = StringUtils.hasText(payload.getTitle())
                ? requireTitle(payload.getTitle())
                : defaultTitle(alert);
        String priority = normalizePriority(payload.getPriority(),
                WorkOrderPriority.fromAlertLevel(alert.getAlertLevel()).name());
        SysUser assignee = payload.getAssigneeId() == null ? null : requireActiveUser(payload.getAssigneeId());

        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo(nextNo());
        order.setTitle(title);
        order.setDescription(StringUtils.hasText(payload.getDescription())
                ? payload.getDescription().trim() : defaultDescription(alert));
        order.setSource(SOURCE_ALERT);
        order.setAlertId(alert.getId());
        order.setStationId(alert.getStationId());
        order.setChargerId(alert.getChargerId());
        order.setDeviceNo(alert.getDeviceNo());
        order.setPortNo(alert.getPortNo());
        order.setPriority(priority);
        order.setStatus(assignee == null ? WorkOrderStatus.OPEN.name() : WorkOrderStatus.PROCESSING.name());
        order.setAssigneeId(assignee == null ? null : assignee.getUserId());
        order.setAssigneeName(assignee == null ? null : displayName(assignee));
        order.setCreator(currentUser());
        workOrderMapper.insert(order);

        writeRecord(order.getId(), "CREATE",
                "由告警 #" + alert.getId() + "（" + alertTypeLabel(alert.getAlertType()) + "）生成工单",
                currentUser());
        if (assignee != null) {
            writeRecord(order.getId(), "ASSIGN", "指派给 " + displayName(assignee), currentUser());
        }
        return toVos(List.of(require(order.getId()))).get(0);
    }

    @Transactional
    public WorkOrderVo assign(Long id, WorkOrderAssignRequest request) {
        WorkOrder order = require(id);
        WorkOrderStatus status = requireStatus(order);
        if (status != WorkOrderStatus.OPEN && status != WorkOrderStatus.PROCESSING) {
            throw new ServiceException(400, "当前状态不能重新指派");
        }
        SysUser assignee = requireActiveUser(request.getAssigneeId());
        if (status == WorkOrderStatus.PROCESSING && Objects.equals(order.getAssigneeId(), assignee.getUserId())) {
            throw new ServiceException(400, "工单已指派给该处理人");
        }
        workOrderMapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, id)
                .set(WorkOrder::getAssigneeId, assignee.getUserId())
                .set(WorkOrder::getAssigneeName, displayName(assignee))
                .set(WorkOrder::getStatus, WorkOrderStatus.PROCESSING.name())
                .set(WorkOrder::getUpdateTime, LocalDateTime.now()));
        writeRecord(id, "ASSIGN", "指派给 " + displayName(assignee), currentUser());
        return toVos(List.of(require(id))).get(0);
    }

    @Transactional
    public WorkOrderVo addRecord(Long id, WorkOrderRecordRequest request) {
        WorkOrder order = require(id);
        WorkOrderStatus status = requireStatus(order);
        if (status != WorkOrderStatus.OPEN && status != WorkOrderStatus.PROCESSING) {
            throw new ServiceException(400, "当前状态不能添加处理记录");
        }
        String content = requireContent(request.getContent(), "处理记录");
        writeRecord(id, "RECORD", content, currentUser());
        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, id)
                .set(WorkOrder::getUpdateTime, LocalDateTime.now());
        if (status == WorkOrderStatus.OPEN) {
            update.set(WorkOrder::getStatus, WorkOrderStatus.PROCESSING.name());
        }
        workOrderMapper.update(null, update);
        return toVos(List.of(require(id))).get(0);
    }

    @Transactional
    public WorkOrderVo resolve(Long id, WorkOrderResolveRequest request) {
        WorkOrder order = require(id);
        WorkOrderStatus status = requireStatus(order);
        if (status != WorkOrderStatus.OPEN && status != WorkOrderStatus.PROCESSING) {
            throw new ServiceException(400, "当前状态不能标记解决");
        }
        String resolution = requireContent(request.getResolution(), "处理结论");
        String operator = currentUser();
        LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, id)
                .set(WorkOrder::getStatus, WorkOrderStatus.RESOLVED.name())
                .set(WorkOrder::getResolution, resolution)
                .set(WorkOrder::getResolvedAt, LocalDateTime.now())
                .set(WorkOrder::getUpdateTime, LocalDateTime.now());
        if (!StringUtils.hasText(order.getAssigneeName())) {
            update.set(WorkOrder::getAssigneeId, SecurityUtils.getUserId())
                    .set(WorkOrder::getAssigneeName, operator);
        }
        workOrderMapper.update(null, update);
        writeRecord(id, "RESOLVE", resolution, operator);
        if (order.getAlertId() != null) {
            try {
                alertService.resolve(order.getAlertId());
            } catch (ServiceException e) {
                if (e.getCode() != 404) {
                    throw e;
                }
            }
        }
        return toVos(List.of(require(id))).get(0);
    }

    @Transactional
    public WorkOrderVo close(Long id, WorkOrderCloseRequest request) {
        WorkOrder order = require(id);
        WorkOrderStatus status = requireStatus(order);
        if (status == WorkOrderStatus.CLOSED) {
            throw new ServiceException(400, "工单已归档");
        }
        if (status != WorkOrderStatus.RESOLVED) {
            throw new ServiceException(400, "请先标记解决再归档");
        }
        String operator = currentUser();
        workOrderMapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, id)
                .set(WorkOrder::getStatus, WorkOrderStatus.CLOSED.name())
                .set(WorkOrder::getClosedBy, operator)
                .set(WorkOrder::getClosedAt, LocalDateTime.now())
                .set(WorkOrder::getUpdateTime, LocalDateTime.now()));
        String remark = request == null ? null : trimToNull(request.getRemark());
        writeRecord(id, "CLOSE", remark == null ? "归档工单" : "归档工单：" + remark, operator);
        return toVos(List.of(require(id))).get(0);
    }

    @Transactional
    public WorkOrderVo reopen(Long id, WorkOrderCloseRequest request) {
        WorkOrder order = require(id);
        WorkOrderStatus status = requireStatus(order);
        if (status != WorkOrderStatus.RESOLVED) {
            throw new ServiceException(400, "仅已解决未归档的工单可以重新打开");
        }
        String operator = currentUser();
        workOrderMapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, id)
                .set(WorkOrder::getStatus, WorkOrderStatus.PROCESSING.name())
                .set(WorkOrder::getResolvedAt, null)
                .set(WorkOrder::getResolution, null)
                .set(WorkOrder::getUpdateTime, LocalDateTime.now()));
        String remark = request == null ? null : trimToNull(request.getRemark());
        writeRecord(id, "REOPEN", remark == null ? "重新打开工单" : "重新打开工单：" + remark, operator);
        return toVos(List.of(require(id))).get(0);
    }

    @Transactional
    public WorkOrderVo update(Long id, WorkOrderUpdateRequest request) {
        WorkOrder order = require(id);
        WorkOrderStatus status = requireStatus(order);
        if (status == WorkOrderStatus.CLOSED) {
            throw new ServiceException(400, "已归档工单不能修改");
        }
        String title = order.getTitle();
        if (StringUtils.hasText(request.getTitle())) {
            title = requireTitle(request.getTitle());
        }
        String priority = order.getPriority();
        if (StringUtils.hasText(request.getPriority())) {
            priority = normalizePriority(request.getPriority(), order.getPriority());
        }
        String description = request.getDescription() == null ? order.getDescription() : trimToNull(request.getDescription());
        workOrderMapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, id)
                .set(WorkOrder::getTitle, title)
                .set(WorkOrder::getDescription, description)
                .set(WorkOrder::getPriority, priority)
                .set(WorkOrder::getUpdateTime, LocalDateTime.now()));
        writeRecord(id, "EDIT", "更新工单信息", currentUser());
        return toVos(List.of(require(id))).get(0);
    }

    @Transactional
    public void delete(Long id) {
        WorkOrder order = require(id);
        if (requireStatus(order) != WorkOrderStatus.CLOSED) {
            throw new ServiceException(400, "仅已归档工单可以删除");
        }
        recordMapper.delete(new LambdaQueryWrapper<WorkOrderRecord>().eq(WorkOrderRecord::getWorkOrderId, id));
        workOrderMapper.deleteById(id);
    }

    private long countByStatus(List<Long> scope, WorkOrderStatus status) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStatus, status.name());
        if (scope != null) {
            wrapper.in(WorkOrder::getStationId, scope);
        }
        return workOrderMapper.selectCount(wrapper);
    }

    private List<WorkOrderVo> toVos(List<WorkOrder> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Station> stations = loadStations(orders);
        Map<Long, Charger> chargers = loadChargers(orders);
        Map<Long, AlertRecord> alerts = loadAlerts(orders);
        Map<Long, RecordStat> recordStats = loadRecordStats(orders);
        return orders.stream().map(order -> {
            WorkOrderVo vo = new WorkOrderVo();
            vo.setId(order.getId());
            vo.setWorkOrderNo(order.getWorkOrderNo());
            vo.setTitle(order.getTitle());
            vo.setDescription(order.getDescription());
            vo.setSource(order.getSource());
            vo.setAlertId(order.getAlertId());
            vo.setStationId(order.getStationId());
            vo.setChargerId(order.getChargerId());
            vo.setDeviceNo(order.getDeviceNo());
            vo.setPortNo(order.getPortNo());
            vo.setPriority(order.getPriority());
            vo.setStatus(order.getStatus());
            vo.setAssigneeId(order.getAssigneeId());
            vo.setAssigneeName(order.getAssigneeName());
            vo.setCreator(order.getCreator());
            vo.setResolution(order.getResolution());
            vo.setClosedBy(order.getClosedBy());
            vo.setResolvedAt(order.getResolvedAt());
            vo.setClosedAt(order.getClosedAt());
            vo.setCreateTime(order.getCreateTime());
            vo.setUpdateTime(order.getUpdateTime());
            Station station = stations.get(order.getStationId());
            if (station != null) {
                vo.setStationName(station.getName());
            }
            Charger charger = chargers.get(order.getChargerId());
            if (charger != null) {
                vo.setDeviceName(charger.getName());
                if (!StringUtils.hasText(vo.getDeviceNo())) {
                    vo.setDeviceNo(charger.getDeviceNo());
                }
            }
            AlertRecord alert = alerts.get(order.getAlertId());
            if (alert != null) {
                vo.setAlertType(alert.getAlertType());
                vo.setAlertLevel(alert.getAlertLevel());
            }
            RecordStat stat = recordStats.get(order.getId());
            if (stat != null) {
                vo.setRecordCount(stat.count());
                vo.setLastRecordAt(stat.lastTime());
            }
            return vo;
        }).toList();
    }

    private Map<Long, Station> loadStations(List<WorkOrder> orders) {
        List<Long> ids = orders.stream().map(WorkOrder::getStationId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return stationMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Station::getId, Function.identity()));
    }

    private Map<Long, Charger> loadChargers(List<WorkOrder> orders) {
        List<Long> ids = orders.stream().map(WorkOrder::getChargerId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return chargerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Charger::getId, Function.identity()));
    }

    private Map<Long, AlertRecord> loadAlerts(List<WorkOrder> orders) {
        List<Long> ids = orders.stream().map(WorkOrder::getAlertId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return alertMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AlertRecord::getId, Function.identity()));
    }

    private Map<Long, RecordStat> loadRecordStats(List<WorkOrder> orders) {
        List<Long> ids = orders.stream().map(WorkOrder::getId).toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<WorkOrderRecord> wrapper = new QueryWrapper<WorkOrderRecord>()
                .select("work_order_id", "COUNT(*) AS record_count", "MAX(create_time) AS last_record_at")
                .in("work_order_id", ids)
                .groupBy("work_order_id");
        Map<Long, RecordStat> stats = new LinkedHashMap<>();
        for (Map<String, Object> row : recordMapper.selectMaps(wrapper)) {
            Object idValue = row.get("work_order_id");
            if (!(idValue instanceof Number number)) {
                continue;
            }
            Object countValue = row.get("record_count");
            long count = countValue instanceof Number c ? c.longValue() : 0L;
            stats.put(number.longValue(), new RecordStat(count, toLocalDateTime(row.get("last_record_at"))));
        }
        return stats;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private WorkOrderRecordVo toRecordVo(WorkOrderRecord record) {
        WorkOrderRecordVo vo = new WorkOrderRecordVo();
        vo.setId(record.getId());
        vo.setAction(record.getAction());
        vo.setContent(record.getContent());
        vo.setOperator(record.getOperator());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private WorkOrder require(Long id) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException(404, "工单不存在");
        }
        scopeService.assertAccess(order.getStationId());
        return order;
    }

    private WorkOrderStatus requireStatus(WorkOrder order) {
        WorkOrderStatus status = WorkOrderStatus.parse(order.getStatus());
        if (status == null) {
            throw new ServiceException(500, "工单状态异常");
        }
        return status;
    }

    private SysUser requireActiveUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(404, "处理人不存在");
        }
        if (!"0".equals(user.getStatus())) {
            throw new ServiceException(400, "处理人账号已停用");
        }
        return user;
    }

    private String normalizePriority(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        WorkOrderPriority priority = WorkOrderPriority.parse(value);
        if (priority == null) {
            throw new ServiceException(400, "优先级不合法");
        }
        return priority.name();
    }

    private String requireTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new ServiceException(400, "请填写工单标题");
        }
        return title.trim();
    }

    private String requireContent(String content, String label) {
        if (!StringUtils.hasText(content)) {
            throw new ServiceException(400, "请填写" + label);
        }
        String value = content.trim();
        if (value.length() > 500) {
            throw new ServiceException(400, label + "不能超过 500 字");
        }
        return value;
    }

    private void checkPortNo(Charger charger, Integer portNo) {
        if (portNo == null) {
            return;
        }
        if (charger == null) {
            throw new ServiceException(400, "选择端口前请先选择设备");
        }
        if (portNo < 1 || (charger.getPortCount() != null && portNo > charger.getPortCount())) {
            throw new ServiceException(400, "端口号超出设备范围");
        }
    }

    private String defaultTitle(AlertRecord alert) {
        StringBuilder title = new StringBuilder(alertTypeLabel(alert.getAlertType()));
        if (StringUtils.hasText(alert.getDeviceNo())) {
            title.append(" · ").append(alert.getDeviceNo());
        }
        if (alert.getPortNo() != null) {
            title.append(" · ").append(alert.getPortNo()).append(" 号口");
        }
        return requireTitle(title.toString());
    }

    private String defaultDescription(AlertRecord alert) {
        StringBuilder text = new StringBuilder();
        text.append("告警内容：").append(StringUtils.hasText(alert.getContent()) ? alert.getContent() : "-");
        if (alert.getMetricValue() != null) {
            text.append("；实测 ").append(alert.getMetricValue().stripTrailingZeros().toPlainString());
        }
        if (alert.getThresholdValue() != null) {
            text.append("；阈值 ").append(alert.getThresholdValue().stripTrailingZeros().toPlainString());
        }
        if (alert.getOccurredAt() != null) {
            text.append("；发生时间 ").append(alert.getOccurredAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        String value = text.toString();
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private String alertTypeLabel(String type) {
        AlertType parsed = AlertType.parse(type);
        return parsed == null ? StringUtils.hasText(type) ? type : "告警" : parsed.getLabel();
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getNickName()) ? user.getNickName() : user.getUserName();
    }

    private String currentUser() {
        String username = SecurityUtils.getUsername();
        return StringUtils.hasText(username) ? username : "system";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void writeRecord(Long workOrderId, String action, String content, String operator) {
        WorkOrderRecord record = new WorkOrderRecord();
        record.setWorkOrderId(workOrderId);
        record.setAction(action);
        record.setContent(content);
        record.setOperator(operator);
        record.setCreateTime(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private String nextNo() {
        String suffix = String.format("%03d", Math.floorMod(SEQUENCE.getAndIncrement(), 1000));
        return "WO" + LocalDateTime.now().format(NUMBER_TIME) + suffix;
    }

    private record RecordStat(long count, LocalDateTime lastTime) {
    }
}
