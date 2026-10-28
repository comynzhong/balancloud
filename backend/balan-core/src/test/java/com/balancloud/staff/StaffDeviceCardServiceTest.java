package com.balancloud.staff;

import com.baomidou.mybatisplus.core.conditions.AbstractLambdaWrapper;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.balancloud.alert.domain.AlertRecord;
import com.balancloud.alert.mapper.AlertRecordMapper;
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
import com.balancloud.framework.security.LoginUser;
import com.balancloud.framework.security.PermissionService;
import com.balancloud.framework.sensitive.SensitiveDataMasker;
import com.balancloud.mqtt.DeviceShadowService;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.staff.service.StaffDeviceCardService;
import com.balancloud.staff.vo.StaffDeviceCardVo;
import com.balancloud.staff.vo.StaffPortVo;
import com.balancloud.system.mapper.SysUserStationMapper;
import com.balancloud.system.service.SystemConfigService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备卡是三个既有查询的聚合读（§50.4）：越权、多单、脱敏都在这里判，不给前端留猜测空间。
 */
class StaffDeviceCardServiceTest {

    private ChargerMapper chargerMapper;
    private ChargerPortMapper portMapper;
    private StationMapper stationMapper;
    private ChargeOrderMapper orderMapper;
    private AlertRecordMapper alertMapper;
    private AppUserMapper appUserMapper;
    private AppVehicleMapper appVehicleMapper;
    private SysUserStationMapper userStationMapper;
    private DeviceShadowService shadowService;
    private StaffDeviceCardService service;

    static {
        // LambdaQueryWrapper 取列名要 MP 的表信息缓存，纯 mock 测试里没有 SqlSessionFactory，这里手工装一次
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("com.balancloud.alert.mapper.AlertRecordMapper");
        TableInfoHelper.initTableInfo(assistant, AlertRecord.class);
    }

    @BeforeEach
    void setUp() {
        chargerMapper = mock(ChargerMapper.class);
        portMapper = mock(ChargerPortMapper.class);
        stationMapper = mock(StationMapper.class);
        orderMapper = mock(ChargeOrderMapper.class);
        alertMapper = mock(AlertRecordMapper.class);
        appUserMapper = mock(AppUserMapper.class);
        appVehicleMapper = mock(AppVehicleMapper.class);
        userStationMapper = mock(SysUserStationMapper.class);
        shadowService = mock(DeviceShadowService.class);
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.isSensitiveMaskEnabled()).thenReturn(true);

        service = new StaffDeviceCardService(chargerMapper, portMapper, stationMapper, orderMapper,
                alertMapper, appUserMapper, appVehicleMapper,
                new StationScopeService(userStationMapper), shadowService,
                new SensitiveDataMasker(configService), new PermissionService());

        when(chargerMapper.selectByDeviceNo(anyString())).thenReturn(charger());
        when(stationMapper.selectById(any())).thenReturn(station());
        when(portMapper.selectList(any())).thenReturn(List.of(port(1), port(2)));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(alertMapper.selectList(any())).thenReturn(List.of());
        when(appUserMapper.selectBatchIds(any())).thenReturn(List.of());
        when(appVehicleMapper.selectBatchIds(any())).thenReturn(List.of());
        when(shadowService.get(anyString())).thenReturn(Map.of());
        login(Set.of("ops"), Set.of("charger:list", "charger:stop"), List.of(1L));
    }

    @Test
    void blankDeviceNumberRejected() {
        assertThatThrownBy(() -> service.deviceCard("   "))
                .isInstanceOf(ServiceException.class)
                .hasMessage("设备编号不能为空");
    }

    @Test
    void missingOrDeletedDeviceReturnsNotFound() {
        when(chargerMapper.selectByDeviceNo(anyString())).thenReturn(null);
        assertThatThrownBy(() -> service.deviceCard("DC0001"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("设备不存在，请检查设备编号");
    }

    @Test
    void stationOutsideGrantRejected() {
        login(Set.of("ops"), Set.of("charger:list"), List.of(9L));
        assertThatThrownBy(() -> service.deviceCard("DC0001"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("无权访问该场站");
    }

    @Test
    void anonymousSessionRejected() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.deviceCard("DC0001"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("认证失败，无法访问系统资源");
    }

    @Test
    void idleDeviceHasPortsButNoCurrentOrder() {
        StaffDeviceCardVo card = service.deviceCard(" DC0001 ");
        assertThat(card.getChargerId()).isEqualTo(11L);
        assertThat(card.getDeviceNo()).isEqualTo("DC0001");
        assertThat(card.getStationName()).isEqualTo("示范场站");
        assertThat(card.getPortList()).extracting(StaffPortVo::getPortNumber).containsExactly(1, 2);
        assertThat(card.getPortList()).allSatisfy(port -> assertThat(port.getCurrentOrder()).isNull());
        assertThat(card.getAlertList()).isEmpty();
    }

    @Test
    void currentOrderCarriesUserAndVehicleWithMasking() {
        ChargeOrder order = new ChargeOrder();
        order.setId(88L);
        order.setOrderNo("CO20261027000001");
        order.setChargerId(11L);
        order.setPortNo(1);
        order.setStatus("CHARGING");
        order.setStartMethod("SCAN");
        order.setStartTime(LocalDateTime.now().minusMinutes(12));
        order.setDurationSeconds(720);
        order.setEnergyKwh(new BigDecimal("1.80"));
        order.setTotalFee(new BigDecimal("2.40"));
        order.setAppUserId(5L);
        order.setVehicleId(6L);
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(appUserMapper.selectBatchIds(any()))
                .thenReturn(List.of(user(5L, "阿中", "13800000001")));
        when(appVehicleMapper.selectBatchIds(any())).thenReturn(List.of(vehicle(6L, "粤B12345")));

        StaffDeviceCardVo card = service.deviceCard("DC0001");
        StaffPortVo first = card.getPortList().get(0);
        StaffPortVo second = card.getPortList().get(1);
        assertThat(first.getCurrentOrder()).isNotNull();
        assertThat(first.getCurrentOrder().getOrderNumber()).isEqualTo("CO20261027000001");
        assertThat(first.getCurrentOrder().getAppUserPhone()).isEqualTo("138****0001");
        assertThat(first.getCurrentOrder().getVehiclePlate()).isEqualTo("粤B***45");
        assertThat(second.getCurrentOrder()).isNull();
    }

    @Test
    void samePortWithTwoCurrentOrdersIsDataError() {
        ChargeOrder first = new ChargeOrder();
        first.setId(91L);
        first.setOrderNo("CO-A");
        first.setPortNo(1);
        first.setStatus("CHARGING");
        ChargeOrder second = new ChargeOrder();
        second.setId(92L);
        second.setOrderNo("CO-B");
        second.setPortNo(1);
        second.setStatus("STOPPING");
        when(orderMapper.selectList(any())).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.deviceCard("DC0001"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("该端口存在多笔进行中订单，数据异常，请联系平台运营");
    }

    @Test
    void shadowMetricsFillLiveValues() {
        PortMetric metric = new PortMetric();
        metric.setPortNo(2);
        metric.setVoltage(380.0);
        metric.setCurrentA(32.5);
        metric.setTemperature(41.0);
        metric.setPowerKw(12.3);
        when(shadowService.get(anyString())).thenReturn(Map.of(2, metric));

        StaffDeviceCardVo card = service.deviceCard("DC0001");
        assertThat(card.getPortList().get(1).getLivePowerKw()).isEqualTo(12.3);
        assertThat(card.getPortList().get(0).getLiveVoltage()).isNull();
    }

    @Test
    void alertWindowQueryExcludesResolvedAndKeepsPortFault() {
        AlertRecord fault = new AlertRecord();
        fault.setId(701L);
        fault.setChargerId(11L);
        fault.setPortNo(2);
        fault.setAlertType("PORT_FAULT");
        fault.setAlertLevel("CRITICAL");
        fault.setStatus("OPEN");
        fault.setContent("端口 2 故障");
        fault.setOccurredAt(LocalDateTime.now().minusHours(30));
        fault.setLastOccurredAt(LocalDateTime.now().minusMinutes(5));
        fault.setAcknowledgedBy("ops01");
        when(alertMapper.selectList(any())).thenReturn(List.of(fault));

        StaffDeviceCardVo card = service.deviceCard("DC0001");
        assertThat(card.getAlertList()).hasSize(1);
        assertThat(card.getAlertList().get(0).getAlertName()).isEqualTo("端口故障");
        assertThat(card.getAlertList().get(0).getAlertAcknowledgedBy()).isEqualTo("ops01");

        ArgumentCaptor<Wrapper<AlertRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(alertMapper).selectList(captor.capture());
        AbstractLambdaWrapper<AlertRecord, ?> query = (AbstractLambdaWrapper<AlertRecord, ?>) captor.getValue();
        // 条件参数要等 SQL 片段生成才落进 paramNameValuePairs，先取一次 WHERE 再断言
        String where = query.getSqlSegment();
        Map<String, Object> params = query.getParamNameValuePairs();
        assertThat(where).contains("status <>").as("未闭环过滤要留在 SQL 里：%s", where);
        assertThat(where).contains("last_occurred_at").as("近 24 小时窗口要留在 SQL 里：%s", where);
        assertThat(params).containsValue("RESOLVED");
        assertThat(params.values()).anyMatch(value -> value instanceof LocalDateTime timestamp
                && timestamp.isAfter(LocalDateTime.now().minusHours(25))
                && timestamp.isBefore(LocalDateTime.now().minusHours(23)));
    }

    @Test
    void operationFlagsComeFromPermissions() {
        assertThat(service.deviceCard("DC0001").isCanStop()).isTrue();
        assertThat(service.deviceCard("DC0001").isCanUnlock()).isFalse();

        login(Set.of("admin"), Set.of("*:*:*"), List.of());
        StaffDeviceCardVo wildcard = service.deviceCard("DC0001");
        assertThat(wildcard.isCanStop()).isTrue();
        assertThat(wildcard.isCanUnlock()).isTrue();

        login(Set.of("watcher"), Set.of("charger:list"), List.of(1L));
        StaffDeviceCardVo readOnly = service.deviceCard("DC0001");
        assertThat(readOnly.isCanStop()).isFalse();
        assertThat(readOnly.isCanUnlock()).isFalse();
    }

    private Charger charger() {
        Charger charger = new Charger();
        charger.setId(11L);
        charger.setStationId(1L);
        charger.setDeviceNo("DC0001");
        charger.setName("四轮直流桩 01");
        charger.setDeviceType("DC_DOUBLE");
        charger.setScene("DC_CAR");
        charger.setProtocol("BALAN_MQTT");
        charger.setStartMethod("SCAN");
        charger.setPortCount(2);
        charger.setOnlineStatus("ONLINE");
        charger.setRatedVoltage(new BigDecimal("380"));
        charger.setRatedPowerKw(new BigDecimal("120"));
        charger.setFirmwareVersion("1.0.3");
        charger.setGroupName("一期");
        return charger;
    }

    private Station station() {
        Station station = new Station();
        station.setId(1L);
        station.setName("示范场站");
        return station;
    }

    private ChargerPort port(int portNo) {
        ChargerPort port = new ChargerPort();
        port.setChargerId(11L);
        port.setPortNo(portNo);
        port.setStatus(portNo == 1 ? "CHARGING" : "IDLE");
        port.setPlugged(portNo == 1 ? "1" : "0");
        port.setCurrentOrderNo(portNo == 1 ? "CO20261027000001" : null);
        port.setRatedPowerKw(new BigDecimal("60"));
        port.setRatedCurrentA(new BigDecimal("125"));
        return port;
    }

    private AppUser user(Long id, String nickname, String phone) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setNickname(nickname);
        user.setPhone(phone);
        return user;
    }

    private AppVehicle vehicle(Long id, String plateNo) {
        AppVehicle vehicle = new AppVehicle();
        vehicle.setId(id);
        vehicle.setPlateNo(plateNo);
        return vehicle;
    }

    private void login(Set<String> roles, Set<String> permissions, List<Long> stationIds) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(31L);
        loginUser.setUsername("ops01");
        loginUser.setNickName("现场运维");
        loginUser.setTenantId(1L);
        loginUser.setRoles(roles);
        loginUser.setPermissions(permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
        org.mockito.Mockito.clearInvocations(userStationMapper);
        when(userStationMapper.selectStationIdsByUserId(31L)).thenReturn(stationIds);
    }
}
