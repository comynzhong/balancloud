package com.balancloud.staff.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.alert.domain.AlertRecord;
import com.balancloud.alert.mapper.AlertRecordMapper;
import com.balancloud.alert.model.AlertStatus;
import com.balancloud.alert.model.AlertType;
import com.balancloud.app.domain.AppUser;
import com.balancloud.app.domain.AppVehicle;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.framework.security.PermissionService;
import com.balancloud.framework.sensitive.SensitiveDataMasker;
import com.balancloud.mqtt.DeviceShadowService;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.staff.vo.StaffAlertVo;
import com.balancloud.staff.vo.StaffCurrentOrderVo;
import com.balancloud.staff.vo.StaffDeviceCardVo;
import com.balancloud.staff.vo.StaffPortVo;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 移动运维工作台的设备卡（§50.5）：设备 + 端口 + 各端口当前订单 + 近 24 小时未闭环告警。
 *
 * 只读聚合，不下发指令；停充/解锁复用既有接口，权限由 PreAuthorize 兜底，
 * 这里的 canStop 与 canUnlock 只用于前端按钮显隐。
 */
@Service
public class StaffDeviceCardService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffDeviceCardService.class);

    /** 一端口一单：只认这两态为当前订单，PENDING_START 还没占住端口。 */
    private static final List<String> CURRENT_ORDER_STATUS = List.of(
            OrderStatus.CHARGING.name(), OrderStatus.STOPPING.name());
    private static final int ALERT_WINDOW_HOURS = 24;
    private static final int ALERT_LIMIT = 20;

    private final ChargerMapper chargerMapper;
    private final ChargerPortMapper portMapper;
    private final StationMapper stationMapper;
    private final ChargeOrderMapper orderMapper;
    private final AlertRecordMapper alertMapper;
    private final AppUserMapper appUserMapper;
    private final AppVehicleMapper appVehicleMapper;
    private final StationScopeService stationScopeService;
    private final DeviceShadowService deviceShadowService;
    private final SensitiveDataMasker masker;
    private final PermissionService permissionService;

    public StaffDeviceCardService(ChargerMapper chargerMapper,
                                  ChargerPortMapper portMapper,
                                  StationMapper stationMapper,
                                  ChargeOrderMapper orderMapper,
                                  AlertRecordMapper alertMapper,
                                  AppUserMapper appUserMapper,
                                  AppVehicleMapper appVehicleMapper,
                                  StationScopeService stationScopeService,
                                  DeviceShadowService deviceShadowService,
                                  SensitiveDataMasker masker,
                                  PermissionService permissionService) {
        this.chargerMapper = chargerMapper;
        this.portMapper = portMapper;
        this.stationMapper = stationMapper;
        this.orderMapper = orderMapper;
        this.alertMapper = alertMapper;
        this.appUserMapper = appUserMapper;
        this.appVehicleMapper = appVehicleMapper;
        this.stationScopeService = stationScopeService;
        this.deviceShadowService = deviceShadowService;
        this.masker = masker;
        this.permissionService = permissionService;
    }

    public StaffDeviceCardVo deviceCard(String deviceNumber) {
        String deviceNo = deviceNumber == null ? "" : deviceNumber.trim();
        if (deviceNo.isEmpty()) {
            throw new ServiceException(400, "设备编号不能为空");
        }
        Charger charger = chargerMapper.selectByDeviceNo(deviceNo);
        if (charger == null) {
            throw new ServiceException(404, "设备不存在，请检查设备编号");
        }
        stationScopeService.assertAccess(charger.getStationId());

        Station station = stationMapper.selectById(charger.getStationId());
        List<ChargerPort> portRows = portMapper.selectList(new LambdaQueryWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, charger.getId())
                .orderByAsc(ChargerPort::getPortNo));
        Map<Integer, ChargeOrder> currentOrders = currentOrdersByPort(charger.getId());
        Map<Integer, PortMetric> shadow = deviceShadowService.get(charger.getDeviceNo());
        Map<Long, AppUser> users = loadUsers(currentOrders.values());
        Map<Long, AppVehicle> vehicles = loadVehicles(currentOrders.values());

        StaffDeviceCardVo card = new StaffDeviceCardVo();
        card.setChargerId(charger.getId());
        card.setDeviceNo(charger.getDeviceNo());
        card.setDeviceName(charger.getName());
        card.setStationId(charger.getStationId());
        card.setStationName(station == null ? null : station.getName());
        card.setDeviceType(charger.getDeviceType());
        card.setScene(charger.getScene());
        card.setProtocol(charger.getProtocol());
        card.setStartMethod(charger.getStartMethod());
        card.setGroupName(charger.getGroupName());
        card.setOnlineStatus(charger.getOnlineStatus());
        card.setLastOnlineAt(charger.getLastOnlineAt());
        card.setLastMetricAt(charger.getLastMetricAt());
        card.setRatedVoltage(charger.getRatedVoltage());
        card.setRatedPowerKw(charger.getRatedPowerKw());
        card.setFirmwareVersion(charger.getFirmwareVersion());
        card.setPortCount(charger.getPortCount());
        card.setCanStop(permissionService.hasPermi("charger:stop"));
        card.setCanUnlock(permissionService.hasPermi("charger:unlock"));
        card.setPortList(portRows.stream()
                .map(row -> toPortVo(row, currentOrders.get(row.getPortNo()),
                        shadow.get(row.getPortNo()), users, vehicles))
                .toList());
        card.setAlertList(recentAlerts(charger.getId()));
        return card;
    }

    /**
     * 同端口多笔进行中订单是数据异常，不是并发巧合：宁可报错也不猜哪一笔是对的。
     */
    private Map<Integer, ChargeOrder> currentOrdersByPort(Long chargerId) {
        List<ChargeOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getChargerId, chargerId)
                .in(ChargeOrder::getStatus, CURRENT_ORDER_STATUS));
        Map<Integer, ChargeOrder> byPort = new HashMap<>();
        for (ChargeOrder order : orders) {
            ChargeOrder keep = byPort.putIfAbsent(order.getPortNo(), order);
            if (keep != null) {
                LOGGER.error("Charger {} port {} has {} current orders: {}, {}",
                        chargerId, order.getPortNo(), orders.size(), keep.getOrderNo(), order.getOrderNo());
                throw new ServiceException(500, "该端口存在多笔进行中订单，数据异常，请联系平台运营");
            }
        }
        return byPort;
    }

    /**
     * 近 24 小时仍在发生且未闭环的告警；PORT_FAULT 不做人工确认不会自动恢复，必须能在这里看到。
     */
    private List<StaffAlertVo> recentAlerts(Long chargerId) {
        LocalDateTime since = LocalDateTime.now().minusHours(ALERT_WINDOW_HOURS);
        List<AlertRecord> records = alertMapper.selectList(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getChargerId, chargerId)
                .ne(AlertRecord::getStatus, AlertStatus.RESOLVED.name())
                .and(nested -> nested.ge(AlertRecord::getLastOccurredAt, since)
                        .or().ge(AlertRecord::getOccurredAt, since))
                .orderByDesc(AlertRecord::getLastOccurredAt)
                .orderByDesc(AlertRecord::getId)
                .last("LIMIT " + ALERT_LIMIT));
        return records.stream().map(this::toAlertVo).toList();
    }

    private StaffAlertVo toAlertVo(AlertRecord record) {
        StaffAlertVo vo = new StaffAlertVo();
        vo.setAlertId(record.getId());
        vo.setAlertPortNo(record.getPortNo());
        vo.setAlertCode(record.getAlertType());
        AlertType type = AlertType.parse(record.getAlertType());
        vo.setAlertName(type == null ? record.getAlertType() : type.getLabel());
        vo.setAlertSeverity(record.getAlertLevel());
        vo.setAlertStatus(record.getStatus());
        vo.setAlertContent(record.getContent());
        vo.setAlertMetricValue(record.getMetricValue());
        vo.setAlertThresholdValue(record.getThresholdValue());
        vo.setAlertOccurredAt(record.getOccurredAt());
        vo.setAlertLastOccurredAt(record.getLastOccurredAt());
        vo.setAlertAcknowledgedBy(record.getAcknowledgedBy());
        return vo;
    }

    private StaffPortVo toPortVo(ChargerPort port, ChargeOrder order, PortMetric metric,
                                 Map<Long, AppUser> users, Map<Long, AppVehicle> vehicles) {
        StaffPortVo vo = new StaffPortVo();
        vo.setPortNumber(port.getPortNo());
        vo.setPortStatus(port.getStatus());
        vo.setPlugged(port.getPlugged());
        vo.setOccupiedSince(port.getOccupiedSince());
        vo.setOccupancyOrderNo(port.getOccupancyOrderNo());
        vo.setCurrentOrderNo(port.getCurrentOrderNo());
        vo.setRatedPowerKw(port.getRatedPowerKw());
        vo.setRatedCurrentA(port.getRatedCurrentA());
        vo.setPortLastMetricAt(port.getLastMetricAt());
        if (metric != null) {
            vo.setLiveVoltage(metric.getVoltage());
            vo.setLiveCurrentA(metric.getCurrentA());
            vo.setLiveTemperature(metric.getTemperature());
            vo.setLivePowerKw(metric.getPowerKw());
        }
        vo.setCurrentOrder(toOrderVo(order, users, vehicles));
        return vo;
    }

    private StaffCurrentOrderVo toOrderVo(ChargeOrder order,
                                          Map<Long, AppUser> users,
                                          Map<Long, AppVehicle> vehicles) {
        if (order == null) {
            return null;
        }
        StaffCurrentOrderVo vo = new StaffCurrentOrderVo();
        vo.setOrderId(order.getId());
        vo.setOrderNumber(order.getOrderNo());
        vo.setOrderStatus(order.getStatus());
        vo.setOrderStartMethod(order.getStartMethod());
        vo.setOrderStartTime(order.getStartTime());
        vo.setDurationSeconds(order.getDurationSeconds());
        vo.setEnergyKwh(order.getEnergyKwh());
        vo.setTotalAmount(order.getTotalFee());
        vo.setMaxDurationMinutes(order.getMaxDurationMinutes());
        vo.setChargeLimitAmount(order.getChargeLimitAmount());
        vo.setVinCode(order.getVin());
        vo.setAppUserId(order.getAppUserId());
        AppUser user = users.get(order.getAppUserId());
        if (user != null) {
            vo.setAppUserNickname(user.getNickname());
            vo.setAppUserPhone(masker.displayPhone(user.getPhone()));
        }
        AppVehicle vehicle = vehicles.get(order.getVehicleId());
        if (vehicle != null) {
            vo.setVehiclePlate(masker.displayPlate(vehicle.getPlateNo()));
        }
        return vo;
    }

    private Map<Long, AppUser> loadUsers(java.util.Collection<ChargeOrder> orders) {
        List<Long> ids = orders.stream().map(ChargeOrder::getAppUserId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return appUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AppUser::getId, item -> item));
    }

    private Map<Long, AppVehicle> loadVehicles(java.util.Collection<ChargeOrder> orders) {
        List<Long> ids = orders.stream().map(ChargeOrder::getVehicleId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return appVehicleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AppVehicle::getId, item -> item));
    }
}
