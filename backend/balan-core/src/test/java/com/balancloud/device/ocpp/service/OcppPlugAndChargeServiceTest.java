package com.balancloud.device.ocpp.service;

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
import com.balancloud.order.service.ChargingService;
import com.balancloud.system.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 插枪即充的准入规则：任何一步不满足都静默拒绝（返回 null），只有全部通过才交给建单服务。
 *
 * <p>「静默」是协议要求：OCPP 侧拿到 null 会回 {@code transactionId=0 + Invalid}，
 * 抛异常会被当成服务器故障重试。
 */
class OcppPlugAndChargeServiceTest {

    private static final long TS = 1_793_000_000_000L;
    private static final String VIN = "LSVAB2BM5EN123456";
    private static final Long USER_ID = 77L;
    private static final Long VEHICLE_ID = 88L;

    private final AppVehicleService vehicleService = mock(AppVehicleService.class);
    private final AppUserMapper userMapper = mock(AppUserMapper.class);
    private final AppBlacklistService blacklistService = mock(AppBlacklistService.class);
    private final AppActiveOrderGuard activeOrderGuard = mock(AppActiveOrderGuard.class);
    private final SystemConfigService configService = mock(SystemConfigService.class);
    private final StationMapper stationMapper = mock(StationMapper.class);
    private final ChargeOrderMapper orderMapper = mock(ChargeOrderMapper.class);
    private final ChargingService chargingService = mock(ChargingService.class);

    private final OcppPlugAndChargeService service = new OcppPlugAndChargeService(vehicleService, userMapper,
            blacklistService, activeOrderGuard, configService, stationMapper, orderMapper, chargingService);

    @BeforeEach
    void stubHappyPath() {
        when(vehicleService.findByVin(VIN)).thenReturn(vehicle());
        when(userMapper.selectById(USER_ID)).thenReturn(user("0"));
        when(stationMapper.selectById(3L)).thenReturn(station("ACTIVE"));
        when(activeOrderGuard.lock(USER_ID)).thenReturn("lock-token");
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(configService.getMaxActiveOrders()).thenReturn(1);
        when(configService.getDefaultMaxDurationMinutes()).thenReturn(480);
        when(configService.getDefaultChargeLimit()).thenReturn(new BigDecimal("50"));
    }

    @Test
    void skipsChargersThatAreNotPlugAndCharge() {
        Charger charger = charger(StartMethod.SCAN);

        assertThat(service.start(charger, 1, VIN, TS)).isNull();
        verifyNoInteractions(vehicleService, chargingService);
    }

    @Test
    void skipsInvalidConnector() {
        Charger charger = charger(StartMethod.PLUG_AND_CHARGE);

        assertThat(service.start(charger, null, VIN, TS)).isNull();
        assertThat(service.start(charger, 0, VIN, TS)).isNull();
        verifyNoInteractions(vehicleService, chargingService);
    }

    @Test
    void skipsBlankVin() {
        assertThat(service.start(charger(StartMethod.PLUG_AND_CHARGE), 1, "  ", TS)).isNull();
        verifyNoInteractions(vehicleService, chargingService);
    }

    @Test
    void skipsVinThatIsNotBoundToAVehicle() {
        when(vehicleService.findByVin(VIN)).thenReturn(null);

        assertThat(service.start(charger(StartMethod.PLUG_AND_CHARGE), 1, VIN, TS)).isNull();
        verifyNoInteractions(chargingService);
    }

    @Test
    void skipsVehicleWithoutOwner() {
        AppVehicle vehicle = vehicle();
        vehicle.setAppUserId(null);
        when(vehicleService.findByVin(VIN)).thenReturn(vehicle);

        assertThat(service.start(charger(StartMethod.PLUG_AND_CHARGE), 1, VIN, TS)).isNull();
        verifyNoInteractions(chargingService);
    }

    @Test
    void skipsWhenOwnerAccountIsGone() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThat(service.start(charger(StartMethod.PLUG_AND_CHARGE), 1, VIN, TS)).isNull();
        verifyNoInteractions(chargingService);
    }

    @Test
    void skipsWhenStationIsNotActive() {
        when(stationMapper.selectById(3L)).thenReturn(station("DISABLED"));

        assertThat(service.start(charger(StartMethod.PLUG_AND_CHARGE), 1, VIN, TS)).isNull();
        verifyNoInteractions(activeOrderGuard, chargingService);
    }

    @Test
    void skipsBlacklistedUserWithoutTakingTheLock() {
        org.mockito.Mockito.doThrow(new ServiceException(403, "账号已被列入充电黑名单"))
                .when(blacklistService).assertNotBlocked(any(AppUser.class), eq(VEHICLE_ID));

        assertThat(service.start(charger(StartMethod.PLUG_AND_CHARGE), 1, VIN, TS)).isNull();
        verify(activeOrderGuard, never()).lock(anyLong());
        verifyNoInteractions(chargingService);
    }

    @Test
    void rejectsWhenUserAlreadyHasTheMaximumActiveOrders() {
        when(orderMapper.selectCount(any())).thenReturn(1L);

        assertThat(service.start(charger(StartMethod.PLUG_AND_CHARGE), 1, VIN, TS)).isNull();
        verify(activeOrderGuard).unlock(USER_ID, "lock-token");
        verifyNoInteractions(chargingService);
    }

    @Test
    void normalizesVinAndDelegatesWithConfiguredDefaults() {
        ChargeOrder created = new ChargeOrder();
        created.setId(501L);
        created.setOrderNo("ORD-PNC-1");
        when(chargingService.startPlugAndCharge(any(), any(), anyLong(), anyLong(), any(), any(), anyLong()))
                .thenReturn(created);

        Charger charger = charger(StartMethod.PLUG_AND_CHARGE);
        ChargeOrder result = service.start(charger, 1, " lsvab2bm5en123456 ", TS);

        assertThat(result).isSameAs(created);
        ArgumentCaptor<StartChargingRequest> captor = ArgumentCaptor.forClass(StartChargingRequest.class);
        verify(chargingService).startPlugAndCharge(eq(charger), eq(1),
                eq(USER_ID), eq(VEHICLE_ID), eq(VIN), captor.capture(), eq(TS));
        assertThat(captor.getValue().getVehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(captor.getValue().getMaxDurationMinutes()).isEqualTo(480);
        assertThat(captor.getValue().getChargeLimitAmount()).isEqualByComparingTo("50");
        verify(activeOrderGuard).unlock(USER_ID, "lock-token");
    }

    @Test
    void swallowsServiceExceptionFromTheOrderServiceAndReleasesTheLock() {
        when(chargingService.startPlugAndCharge(any(), any(), anyLong(), anyLong(), any(), any(), anyLong()))
                .thenThrow(new ServiceException(409, "端口状态已变化，插枪即充建单失败"));

        assertThat(service.start(charger(StartMethod.PLUG_AND_CHARGE), 1, VIN, TS)).isNull();
        verify(activeOrderGuard).unlock(USER_ID, "lock-token");
    }

    @Test
    void swallowsLockContention() {
        when(activeOrderGuard.lock(USER_ID)).thenThrow(new ServiceException(409, "正在处理上一笔操作，请稍后重试"));

        assertThat(service.start(charger(StartMethod.PLUG_AND_CHARGE), 1, VIN, TS)).isNull();
        verify(activeOrderGuard, never()).unlock(anyLong(), any());
        verifyNoInteractions(chargingService);
    }

    private static Charger charger(String startMethod) {
        Charger charger = new Charger();
        charger.setId(9L);
        charger.setStationId(3L);
        charger.setDeviceNo("CP-AC-001");
        charger.setStartMethod(startMethod);
        return charger;
    }

    private static AppVehicle vehicle() {
        AppVehicle vehicle = new AppVehicle();
        vehicle.setId(VEHICLE_ID);
        vehicle.setAppUserId(USER_ID);
        vehicle.setVin(VIN);
        return vehicle;
    }

    private static AppUser user(String status) {
        AppUser user = new AppUser();
        user.setId(USER_ID);
        user.setStatus(status);
        return user;
    }

    private static Station station(String status) {
        Station station = new Station();
        station.setId(3L);
        station.setStatus(status);
        return station;
    }
}
