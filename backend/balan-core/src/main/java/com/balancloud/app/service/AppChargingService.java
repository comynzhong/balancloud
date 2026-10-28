package com.balancloud.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.app.security.AppLoginUser;
import com.balancloud.app.security.AppSecurityUtils;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.dto.StartChargingRequest;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.ChargingActor;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.order.service.ChargingService;
import com.balancloud.order.vo.ChargingActionVo;
import com.balancloud.system.service.SystemConfigService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AppChargingService {

    private final AppActiveOrderGuard activeOrderGuard;
    private final AppVehicleService vehicleService;
    private final AppBlacklistService blacklistService;
    private final AppUserMapper userMapper;
    private final ChargingService chargingService;
    private final ChargerMapper chargerMapper;
    private final StationMapper stationMapper;
    private final ChargeOrderMapper orderMapper;
    private final SystemConfigService configService;

    public AppChargingService(AppActiveOrderGuard activeOrderGuard,
                               AppVehicleService vehicleService,
                               AppBlacklistService blacklistService,
                               AppUserMapper userMapper,
                               ChargingService chargingService,
                               ChargerMapper chargerMapper,
                               StationMapper stationMapper,
                               ChargeOrderMapper orderMapper,
                               SystemConfigService configService) {
        this.activeOrderGuard = activeOrderGuard;
        this.vehicleService = vehicleService;
        this.blacklistService = blacklistService;
        this.userMapper = userMapper;
        this.chargingService = chargingService;
        this.chargerMapper = chargerMapper;
        this.stationMapper = stationMapper;
        this.orderMapper = orderMapper;
        this.configService = configService;
    }

    public ChargingActionVo start(Long chargerId, Integer portNo, StartChargingRequest request) {
        if (configService.isMaintenanceEnabled()) {
            throw new ServiceException(503, configService.getMaintenanceMessage());
        }
        int maxDuration = configService.getDefaultMaxDurationMinutes();
        BigDecimal maxAmount = configService.getDefaultChargeLimit();
        int maxActiveOrders = configService.getMaxActiveOrders();

        AppLoginUser user = AppSecurityUtils.currentUser();
        String lockToken = activeOrderGuard.lock(user.getUserId());
        try {
            if (request.getMaxDurationMinutes() > maxDuration) {
                throw new ServiceException(400, "单笔充电时长不能超过 " + maxDuration + " 分钟");
            }
            if (request.getChargeLimitAmount().compareTo(maxAmount) > 0) {
                throw new ServiceException(400, "单笔金额上限不能超过 " + maxAmount.stripTrailingZeros().toPlainString() + " 元");
            }
            Charger charger = chargerMapper.selectById(chargerId);
            if (charger == null) {
                throw new ServiceException(404, "设备不存在");
            }
            Station station = stationMapper.selectById(charger.getStationId());
            if (station == null || !"ACTIVE".equals(station.getStatus())) {
                throw new ServiceException(400, "场站暂停运营，暂不能启动充电");
            }
            long activeOrders = orderMapper.selectCount(new LambdaQueryWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getAppUserId, user.getUserId())
                    .in(ChargeOrder::getStatus, activeStatuses()));
            if (activeOrders >= maxActiveOrders) {
                throw new ServiceException(409, "您有进行中的充电订单，请先结束后再启动");
            }
            if (request.getVehicleId() != null) {
                vehicleService.validateVehicle(request.getVehicleId(), user.getUserId());
            }
            blacklistService.assertNotBlocked(userMapper.selectById(user.getUserId()), request.getVehicleId());
            return chargingService.start(ChargingActor.appUser(user.getUserId(), user.getNickname()),
                    chargerId, portNo, request);
        } finally {
            activeOrderGuard.unlock(user.getUserId(), lockToken);
        }
    }

    public ChargingActionVo stop(Long orderId) {
        AppLoginUser user = AppSecurityUtils.currentUser();
        ChargeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        if (order.getAppUserId() == null || !order.getAppUserId().equals(user.getUserId())) {
            throw new ServiceException(403, "无权操作该订单");
        }
        if (!OrderStatus.parse(order.getStatus()).isActive()) {
            throw new ServiceException(400, "订单已结束，无需停止");
        }
        return chargingService.stop(ChargingActor.appUser(user.getUserId(), user.getNickname()),
                order.getChargerId(), order.getPortNo());
    }

    private List<String> activeStatuses() {
        return List.of(OrderStatus.PENDING_START.name(), OrderStatus.CHARGING.name(),
                OrderStatus.STOPPING.name());
    }
}
