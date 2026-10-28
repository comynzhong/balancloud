package com.balancloud.order.flow;

import com.balancloud.command.service.CommandService;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.domain.StartMethod;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.protocol.ProtocolAdapterRegistry;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.framework.tenant.TenantTaskExecutor;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.dto.StartChargingRequest;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.ChargingActor;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.order.service.BillingService;
import com.balancloud.order.service.ChargingService;
import com.balancloud.order.service.OccupancyService;
import com.balancloud.order.service.PortLockService;
import com.balancloud.rate.domain.RateStrategy;
import com.balancloud.rate.service.RateService;
import com.balancloud.rate.vo.EnabledRate;
import com.balancloud.spi.MemberBenefitService;
import com.balancloud.spi.ReservationGuard;
import com.balancloud.spi.SettlementService;
import com.balancloud.spi.dto.BenefitItem;
import com.balancloud.support.TableInfoSupport;
import com.balancloud.system.service.MessageService;
import com.balancloud.ws.EventStreamService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 即插即充建单：设备已经上报 StartTransaction，平台直接落一笔 CHARGING 订单。
 * 这里验证与扫码启动的三条分界——不走指令、订单归属车主但操作者是桩、重复交易复用同一笔订单。
 */
class PlugAndChargeChargingServiceTest {

    private static final long CHARGER_ID = 9L;
    private static final long STATION_ID = 3L;
    private static final long APP_USER_ID = 77L;
    private static final long VEHICLE_ID = 88L;
    private static final Integer PORT_NO = 1;
    private static final String VIN = "LSVAB2BM5EN123456";
    private static final long TS = Instant.parse("2026-10-27T02:00:00Z").toEpochMilli();

    private ChargeOrderMapper orderMapper;
    private ChargerMapper chargerMapper;
    private ChargerPortMapper portMapper;
    private RateService rateService;
    private BillingService billingService;
    private CommandService commandService;
    private PortLockService portLockService;
    private StationScopeService scopeService;
    private OccupancyService occupancyService;
    private EventStreamService eventStreamService;
    private MessageService messageService;
    private TenantTaskExecutor tenantTasks;
    private ReservationGuard reservationGuard;
    private SettlementService settlementService;
    private MemberBenefitService memberBenefitService;
    private ChargingService chargingService;
    private Charger charger;
    private ChargerPort port;

    @BeforeAll
    static void initTableInfo() {
        TableInfoSupport.init(Charger.class, ChargerPort.class, ChargeOrder.class);
    }

    @BeforeEach
    void setUp() {
        orderMapper = mock(ChargeOrderMapper.class);
        chargerMapper = mock(ChargerMapper.class);
        portMapper = mock(ChargerPortMapper.class);
        rateService = mock(RateService.class);
        billingService = mock(BillingService.class);
        commandService = mock(CommandService.class);
        portLockService = mock(PortLockService.class);
        scopeService = mock(StationScopeService.class);
        occupancyService = mock(OccupancyService.class);
        eventStreamService = mock(EventStreamService.class);
        messageService = mock(MessageService.class);
        tenantTasks = mock(TenantTaskExecutor.class);
        reservationGuard = mock(ReservationGuard.class);
        settlementService = mock(SettlementService.class);
        memberBenefitService = mock(MemberBenefitService.class);

        chargingService = new ChargingService(orderMapper, chargerMapper, portMapper, rateService,
                billingService, commandService, portLockService, scopeService, occupancyService,
                eventStreamService, messageService, tenantTasks, reservationGuard, settlementService,
                memberBenefitService, flows(), adapters(), noOpTransactions());

        when(portLockService.lock(CHARGER_ID, PORT_NO)).thenReturn("lock-token");
        when(portMapper.update(isNull(), any())).thenReturn(1);
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(rateService.requireEnabled(STATION_ID)).thenReturn(enabledRate());
        when(memberBenefitService.evaluate(any())).thenReturn(List.<BenefitItem>of());
        when(billingService.snapshotJson(any(), any())).thenReturn("{}");
        doAnswer(invocation -> {
            ChargeOrder order = invocation.getArgument(0);
            order.setId(501L);
            return 1;
        }).when(orderMapper).insert(any(ChargeOrder.class));

        charger = charger(StartMethod.PLUG_AND_CHARGE);
        port = port("IDLE");
        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger);
        when(portMapper.selectOne(any())).thenReturn(port);
    }

    @Test
    void createsAChargingOrderWithoutAnyCommand() {
        ChargeOrder order = chargingService.startPlugAndCharge(
                charger, PORT_NO, APP_USER_ID, VEHICLE_ID, VIN, request(), TS);

        assertThat(order).isNotNull();
        assertThat(order.getId()).isEqualTo(501L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CHARGING.name());
        assertThat(order.getStartMethod()).isEqualTo(StartMethod.PLUG_AND_CHARGE);
        assertThat(order.getVin()).isEqualTo(VIN);
        assertThat(order.getAppUserId()).isEqualTo(APP_USER_ID);
        assertThat(order.getVehicleId()).isEqualTo(VEHICLE_ID);
        // 操作者是桩，不是小程序用户
        assertThat(order.getCreateBy()).isEqualTo("device");
        assertThat(order.getPortNo()).isEqualTo(PORT_NO);
        assertThat(order.getStartTime()).isEqualTo(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(TS), ZoneId.of("Asia/Shanghai")));
        // 没有 START 指令可发
        verifyNoInteractions(commandService);
        verify(portMapper).update(isNull(), any());
        verify(portLockService).unlock(CHARGER_ID, PORT_NO, "lock-token");
    }

    @Test
    void returnsTheExistingActiveOrderInsteadOfCreatingASecondOne() {
        ChargeOrder existing = new ChargeOrder();
        existing.setId(600L);
        existing.setStatus(OrderStatus.CHARGING.name());
        when(orderMapper.selectOne(any())).thenReturn(existing);

        ChargeOrder order = chargingService.startPlugAndCharge(
                charger, PORT_NO, APP_USER_ID, VEHICLE_ID, VIN, request(), TS);

        assertThat(order).isSameAs(existing);
        verify(orderMapper, never()).insert(any(ChargeOrder.class));
    }

    @Test
    void scanModeChargersNeverEnterThePlugAndChargePath() {
        Charger scanOnly = charger(StartMethod.SCAN);

        assertThat(chargingService.startPlugAndCharge(
                scanOnly, PORT_NO, APP_USER_ID, VEHICLE_ID, VIN, request(), TS)).isNull();

        verify(portLockService, never()).lock(any(), any());
        verify(orderMapper, never()).insert(any(ChargeOrder.class));
    }

    @Test
    void rejectsPortsThatAreNotIdleOrPreparing() {
        port.setStatus("FAULT");
        when(portMapper.selectOne(any())).thenReturn(port);

        assertThatThrownBy(() -> chargingService.startPlugAndCharge(
                charger, PORT_NO, APP_USER_ID, VEHICLE_ID, VIN, request(), TS))
                .isInstanceOf(ServiceException.class)
                .hasMessage("交流桩当前不是可启动状态（插枪后等待启动）")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        verify(orderMapper, never()).insert(any(ChargeOrder.class));
        verify(portLockService).unlock(CHARGER_ID, PORT_NO, "lock-token");
    }

    @Test
    void releasesPrepaidOccupancyWhenTheGunIsAlreadyPlugged() {
        port.setStatus("OCCUPIED");
        when(portMapper.selectOne(any())).thenReturn(port);

        chargingService.startPlugAndCharge(charger, PORT_NO, APP_USER_ID, VEHICLE_ID, VIN, request(), TS);

        verify(occupancyService).releaseForStart(CHARGER_ID, PORT_NO);
    }

    @Test
    void portRaceIsReportedAs409AfterAnotherWriterWins() {
        when(portMapper.selectOne(any())).thenReturn(port);
        when(portMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> chargingService.startPlugAndCharge(
                charger, PORT_NO, APP_USER_ID, VEHICLE_ID, VIN, request(), TS))
                .isInstanceOf(ServiceException.class)
                .hasMessage("端口状态已变化，插枪即充建单失败")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));
        verify(portLockService).unlock(CHARGER_ID, PORT_NO, "lock-token");
    }

    @Test
    void scanStartRefusesPlugAndChargeDevicesWithAClearMessage() {
        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger);

        assertThatThrownBy(() -> chargingService.start(
                ChargingActor.appUser(APP_USER_ID, "车主"), CHARGER_ID, PORT_NO, request()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("该设备为插枪即充模式，插枪后由设备自动开始充电，请勿扫码启动")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        verify(portLockService, never()).lock(any(), any());
    }

    private static OrderFlowRegistry flows() {
        return new OrderFlowRegistry(List.of(new TwoWheelOrderFlow(), new DcCarOrderFlow(), new AcCarOrderFlow()));
    }

    private static ProtocolAdapterRegistry adapters() {
        return new ProtocolAdapterRegistry(List.of());
    }

    private static Charger charger(String startMethod) {
        Charger charger = new Charger();
        charger.setId(CHARGER_ID);
        charger.setStationId(STATION_ID);
        charger.setDeviceNo("CP-AC-001");
        charger.setProtocol("OCPP16J");
        charger.setScene(AcCarOrderFlow.SCENE);
        charger.setStartMethod(startMethod);
        charger.setOnlineStatus("ONLINE");
        return charger;
    }

    private static ChargerPort port(String status) {
        ChargerPort port = new ChargerPort();
        port.setId(91L);
        port.setChargerId(CHARGER_ID);
        port.setPortNo(PORT_NO);
        port.setStatus(status);
        port.setPlugged("1");
        return port;
    }

    private static StartChargingRequest request() {
        StartChargingRequest request = new StartChargingRequest();
        request.setMaxDurationMinutes(480);
        request.setChargeLimitAmount(new BigDecimal("30.0000"));
        return request;
    }

    private static EnabledRate enabledRate() {
        RateStrategy strategy = new RateStrategy();
        strategy.setId(5L);
        strategy.setName("测试费率");
        strategy.setBillingMode("ENERGY");
        return new EnabledRate(strategy, List.of(), null, List.of());
    }

    /** 建单方法带 @Transactional，测试里不需要真的开事务。 */
    private static PlatformTransactionManager noOpTransactions() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
