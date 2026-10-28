package com.balancloud.device.ocpp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.app.domain.AppUser;
import com.balancloud.app.domain.AppVehicle;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.service.AppActiveOrderGuard;
import com.balancloud.app.service.AppBlacklistService;
import com.balancloud.app.service.AppVehicleService;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.Station;
import com.balancloud.device.domain.StartMethod;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.dto.StartChargingRequest;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.order.service.ChargingService;
import com.balancloud.system.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 交流桩即插即充：设备直接上报 StartTransaction，平台按 {@code idTag = VIN} 反查车主并建单。
 *
 * <p>与扫码启动的差别只有一处：没有「先建 PENDING_START、再发 RemoteStart」那一步。
 * 任何前置条件不满足都返回 null，由 {@link OcppOrderBridge} 回 {@code transactionId=0 + Invalid}
 * 让桩停机——不抛异常，避免 OCPP 线程把「业务拒绝」当成服务器故障重试。
 */
@Service
public class OcppPlugAndChargeService {

    private static final Logger log = LoggerFactory.getLogger(OcppPlugAndChargeService.class);
    private static final List<String> ACTIVE_STATUSES = List.of(
            OrderStatus.PENDING_START.name(), OrderStatus.CHARGING.name(), OrderStatus.STOPPING.name());

    private final AppVehicleService vehicleService;
    private final AppUserMapper userMapper;
    private final AppBlacklistService blacklistService;
    private final AppActiveOrderGuard activeOrderGuard;
    private final SystemConfigService configService;
    private final StationMapper stationMapper;
    private final ChargeOrderMapper orderMapper;
    private final ChargingService chargingService;

    public OcppPlugAndChargeService(AppVehicleService vehicleService,
                                    AppUserMapper userMapper,
                                    AppBlacklistService blacklistService,
                                    AppActiveOrderGuard activeOrderGuard,
                                    SystemConfigService configService,
                                    StationMapper stationMapper,
                                    ChargeOrderMapper orderMapper,
                                    ChargingService chargingService) {
        this.vehicleService = vehicleService;
        this.userMapper = userMapper;
        this.blacklistService = blacklistService;
        this.activeOrderGuard = activeOrderGuard;
        this.configService = configService;
        this.stationMapper = stationMapper;
        this.orderMapper = orderMapper;
        this.chargingService = chargingService;
    }

    /**
     * @return 建好的订单；任何一步前置条件不满足都返回 null（调用方拒绝这笔交易）
     */
    public ChargeOrder start(Charger charger, Integer connectorId, String vin, long ts) {
        if (connectorId == null || connectorId <= 0 || !StartMethod.isPlugAndCharge(charger.getStartMethod())) {
            return null;
        }
        if (!StringUtils.hasText(vin)) {
            log.info("Plug&charge needs VIN as idTag on {} connector {}",
                    charger.getDeviceNo(), connectorId);
            return null;
        }
        String normalizedVin = vin.trim().toUpperCase(Locale.ROOT);
        AppVehicle vehicle = vehicleService.findByVin(normalizedVin);
        if (vehicle == null || vehicle.getAppUserId() == null) {
            log.info("Plug&charge rejected: VIN {} is not bound to a vehicle on {} connector {}",
                    normalizedVin, charger.getDeviceNo(), connectorId);
            return null;
        }
        AppUser user = userMapper.selectById(vehicle.getAppUserId());
        if (user == null) {
            log.info("Plug&charge rejected: owner {} of VIN {} not found", vehicle.getAppUserId(), normalizedVin);
            return null;
        }
        Station station = stationMapper.selectById(charger.getStationId());
        if (station == null || !"ACTIVE".equals(station.getStatus())) {
            log.info("Plug&charge rejected: station {} is not active", charger.getStationId());
            return null;
        }

        String lockToken = null;
        try {
            blacklistService.assertNotBlocked(user, vehicle.getId());
            lockToken = activeOrderGuard.lock(user.getId());
            long activeOrders = orderMapper.selectCount(new LambdaQueryWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getAppUserId, user.getId())
                    .in(ChargeOrder::getStatus, ACTIVE_STATUSES));
            if (activeOrders >= configService.getMaxActiveOrders()) {
                log.info("Plug&charge rejected: user {} already has an active order", user.getId());
                return null;
            }
            StartChargingRequest request = new StartChargingRequest();
            request.setVehicleId(vehicle.getId());
            request.setMaxDurationMinutes(configService.getDefaultMaxDurationMinutes());
            request.setChargeLimitAmount(configService.getDefaultChargeLimit());
            return chargingService.startPlugAndCharge(charger, connectorId, user.getId(),
                    vehicle.getId(), normalizedVin, request, ts);
        } catch (ServiceException e) {
            log.info("Plug&charge rejected on {} connector {}: {}",
                    charger.getDeviceNo(), connectorId, e.getMessage());
            return null;
        } finally {
            if (lockToken != null) {
                activeOrderGuard.unlock(user.getId(), lockToken);
            }
        }
    }
}
