package com.balancloud.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.app.domain.AppUser;
import com.balancloud.app.domain.AppVehicle;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.framework.sensitive.SensitiveDataMasker;
import com.balancloud.mqtt.DeviceShadowService;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.order.vo.ActiveChargingVo;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ActiveChargingService {

    private static final List<String> ACTIVE_STATUSES = List.of(
            OrderStatus.PENDING_START.name(), OrderStatus.CHARGING.name(), OrderStatus.STOPPING.name());
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ChargeOrderMapper orderMapper;
    private final ChargerMapper chargerMapper;
    private final StationMapper stationMapper;
    private final AppUserMapper appUserMapper;
    private final AppVehicleMapper vehicleMapper;
    private final StationScopeService scopeService;
    private final DeviceShadowService shadowService;
    private final SensitiveDataMasker masker;

    public ActiveChargingService(ChargeOrderMapper orderMapper,
                                 ChargerMapper chargerMapper,
                                 StationMapper stationMapper,
                                 AppUserMapper appUserMapper,
                                 AppVehicleMapper vehicleMapper,
                                 StationScopeService scopeService,
                                 DeviceShadowService shadowService,
                                 SensitiveDataMasker masker) {
        this.orderMapper = orderMapper;
        this.chargerMapper = chargerMapper;
        this.stationMapper = stationMapper;
        this.appUserMapper = appUserMapper;
        this.vehicleMapper = vehicleMapper;
        this.scopeService = scopeService;
        this.shadowService = shadowService;
        this.masker = masker;
    }

    public List<ActiveChargingVo> list(Long stationId) {
        if (stationId != null) {
            scopeService.assertAccess(stationId);
        }
        List<Long> scope = scopeService.authorizedStationIds();
        if (scope != null && scope.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<ChargeOrder> wrapper = new LambdaQueryWrapper<ChargeOrder>()
                .in(ChargeOrder::getStatus, ACTIVE_STATUSES)
                .eq(stationId != null, ChargeOrder::getStationId, stationId)
                .orderByDesc(ChargeOrder::getId);
        if (scope != null) {
            wrapper.in(ChargeOrder::getStationId, scope);
        }
        List<ChargeOrder> orders = orderMapper.selectList(wrapper);
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Charger> chargers = chargerMapper.selectBatchIds(
                        orders.stream().map(ChargeOrder::getChargerId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Charger::getId, item -> item));
        Map<Long, Station> stations = stationMapper.selectBatchIds(
                        orders.stream().map(ChargeOrder::getStationId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Station::getId, item -> item));
        Map<Long, AppUser> users = loadUsers(orders);
        Map<Long, AppVehicle> vehicles = loadVehicles(orders);

        return orders.stream().map(order -> {
            ActiveChargingVo vo = new ActiveChargingVo();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setStationId(order.getStationId());
            vo.setChargerId(order.getChargerId());
            vo.setPortNo(order.getPortNo());
            vo.setStatus(order.getStatus());
            vo.setStartTime(order.getStartTime());
            vo.setDurationSeconds(order.getDurationSeconds());
            vo.setEnergyKwh(order.getEnergyKwh());
            vo.setTotalFee(order.getTotalFee());
            vo.setMaxDurationMinutes(order.getMaxDurationMinutes());
            vo.setChargeLimitAmount(order.getChargeLimitAmount());
            vo.setLastMetricAt(toTime(order.getLastMetricTs()));
            vo.setVehicleId(order.getVehicleId());

            Charger charger = chargers.get(order.getChargerId());
            if (charger != null) {
                vo.setDeviceNo(charger.getDeviceNo());
                vo.setDeviceName(charger.getName());
                PortMetric metric = shadowService.get(charger.getDeviceNo()).get(order.getPortNo());
                if (metric != null) {
                    vo.setCurrentPowerKw(metric.getPowerKw());
                }
            }
            Station station = stations.get(order.getStationId());
            if (station != null) {
                vo.setStationName(station.getName());
            }
            AppUser user = users.get(order.getAppUserId());
            if (user != null) {
                vo.setAppUserId(user.getId());
                vo.setAppUserNickname(user.getNickname());
                vo.setAppUserPhone(masker.displayPhone(user.getPhone()));
                vo.setAppUserAvatar(user.getAvatar());
            }
            AppVehicle vehicle = vehicles.get(order.getVehicleId());
            if (vehicle != null) {
                vo.setVehiclePlateNo(masker.displayPlate(vehicle.getPlateNo()));
            }
            return vo;
        }).toList();
    }

    private Map<Long, AppUser> loadUsers(List<ChargeOrder> orders) {
        List<Long> ids = orders.stream().map(ChargeOrder::getAppUserId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return appUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AppUser::getId, item -> item));
    }

    private Map<Long, AppVehicle> loadVehicles(List<ChargeOrder> orders) {
        List<Long> ids = orders.stream().map(ChargeOrder::getVehicleId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return vehicleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AppVehicle::getId, item -> item));
    }

    private LocalDateTime toTime(Long ts) {
        return ts == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE);
    }
}
