package com.balancloud.order.flow;

import com.balancloud.command.domain.DeviceCommand;
import com.balancloud.command.event.CommandResultEvent;
import com.balancloud.command.model.CommandType;
import com.balancloud.command.service.CommandService;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.protocol.BalanMqttAdapter;
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
import com.balancloud.order.vo.ChargingActionVo;
import com.balancloud.rate.domain.RateStrategy;
import com.balancloud.rate.service.RateService;
import com.balancloud.rate.vo.EnabledRate;
import com.balancloud.spi.MemberBenefitService;
import com.balancloud.spi.ReservationGuard;
import com.balancloud.spi.SettlementService;
import com.balancloud.spi.dto.BenefitItem;
import com.balancloud.support.TableInfoSupport;
import com.balancloud.system.service.MessageService;
import com.balancloud.system.service.SystemConfigService;
import com.balancloud.ws.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-2 的反证：{@link TestSceneOrderFlow} 注册进来之后，启动准入、ack 语义、停止超时与占位判定
 * 全部按场景走，而 {@link ChargingService} / {@link OccupancyService} 一行都没改。
 *
 * <p>同一个服务实例、同一段调用代码，只换 {@code charger.scene}，行为就不同——
 * 这就是「新增场景不改 core 业务代码」这条缝的验收方式。
 */
class TestSceneReverseProofTest {

    private static final long CHARGER_ID = 9L;
    private static final long STATION_ID = 3L;
    private static final Integer PORT_NO = 1;

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

        when(portLockService.lock(CHARGER_ID, PORT_NO)).thenReturn("lock");
        doAnswer(invocation -> {
            Runnable body = invocation.getArgument(3);
            body.run();
            return null;
        }).when(tenantTasks).forEach(anyString(), any(Duration.class), any(Duration.class), any(Runnable.class));
    }

    private static OrderFlowRegistry flows() {
        return new OrderFlowRegistry(List.of(new TwoWheelOrderFlow(), new TestSceneOrderFlow()));
    }

    private static ProtocolAdapterRegistry adapters() {
        return new ProtocolAdapterRegistry(List.of(new BalanMqttAdapter(new ObjectMapper())));
    }

    @Test
    void unpluggedStartIsRejectedOnlyForTheTestScene() {
        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger(TestSceneOrderFlow.SCENE));
        when(portMapper.selectOne(any())).thenReturn(port("0"));
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> chargingService.start(actor(), CHARGER_ID, PORT_NO, request()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("该场景必须插枪后才能启动")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        verify(orderMapper, never()).insert(any(ChargeOrder.class));
        verify(commandService, never()).create(any(), any(), any(), any(), any());

        // 同一段启动代码、同一个服务实例：两轮场景不要求插枪，照常建单
        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger("TWOWHEEL"));
        stubStartDependencies();
        ChargingActionVo created = chargingService.start(actor(), CHARGER_ID, PORT_NO, request());

        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING_START.name());
        verify(orderMapper).insert(any(ChargeOrder.class));
    }

    @Test
    void startAckOnlyHandsOverToPreChargePhasesForTheTestScene() {
        ChargeOrder order = pendingStartOrder();
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger(TestSceneOrderFlow.SCENE));

        chargingService.onCommandResult(new CommandResultEvent(command(), "SUCCESS"));

        // ack 只代表握手成功：订单状态、端口状态都不许动，真正的开始由设备侧阶段推进
        verify(orderMapper, never()).update(isNull(), any());
        verify(portMapper, never()).update(isNull(), any());

        // 对照两轮：同样的 ack 立刻进 CHARGING
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger("TWOWHEEL"));
        chargingService.onCommandResult(new CommandResultEvent(command(), "SUCCESS"));

        verify(orderMapper).update(isNull(), any());
        verify(portMapper).update(isNull(), any());
    }

    @Test
    void stoppingSweepUsesTheSceneTimeoutInsteadOfFifteenSeconds() {
        ChargeOrder order = stoppingOrder(20);
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(orderMapper.selectById(order.getId())).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        // TEST_SCENE 的 45 秒：20 秒的 STOPPING 订单还在阈值内，不结算
        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger(TestSceneOrderFlow.SCENE));
        chargingService.sweepStoppingOrders();
        verify(settlementService, never()).afterSettle(any());

        // 同样 20 秒，两轮 15 秒阈值必须先把它结掉
        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger("TWOWHEEL"));
        chargingService.sweepStoppingOrders();
        verify(settlementService).afterSettle(any());

        // 越过 45 秒之后 TEST_SCENE 也要结算
        reset(settlementService);
        order.setUpdateTime(LocalDateTime.now().minusSeconds(50));
        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger(TestSceneOrderFlow.SCENE));
        chargingService.sweepStoppingOrders();
        verify(settlementService).afterSettle(any());
    }

    @Test
    void testSceneNeverChargesOccupancyEvenWhenEveryPreconditionHolds() {
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.isOccupancyEnabled()).thenReturn(true);
        OccupancyService service = new OccupancyService(orderMapper, portMapper, chargerMapper,
                configService, eventStreamService, tenantTasks, flows());
        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger(TestSceneOrderFlow.SCENE));

        ChargeOrder order = new ChargeOrder();
        order.setChargerId(CHARGER_ID);
        order.setAppUserId(77L);
        ChargerPort port = port("1");
        port.setStatus("IDLE");

        // 开启占位、属于小程序用户、枪还插着——两轮会占用，车桩场景直接释放
        assertThat(service.canOccupy(order, port)).isFalse();

        when(chargerMapper.selectById(CHARGER_ID)).thenReturn(charger("TWOWHEEL"));
        assertThat(service.canOccupy(order, port)).isTrue();
    }

    @Test
    void anInstanceWithoutTheNewSceneFallsBackToTwoWheel() {
        OrderFlowRegistry lean = new OrderFlowRegistry(List.of(new TwoWheelOrderFlow()));
        OrderFlowRegistry full = flows();

        // 存量行没有 scene 时按两轮处理；少注册一个场景只影响新设备，不影响默认场景
        assertThat(lean.require(null)).isInstanceOf(TwoWheelOrderFlow.class);
        assertThat(full.require(TestSceneOrderFlow.SCENE)).isInstanceOf(TestSceneOrderFlow.class);
        assertThatThrownBy(() -> lean.require(TestSceneOrderFlow.SCENE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("场景流程未注册: TEST_SCENE");
    }

    @Test
    void noFlowCanReturnAStatusOutsideTheFourValueVocabulary() {
        Set<String> vocabulary = Set.of("IDLE", "CHARGING", "OCCUPIED", "FAULT");
        for (OrderFlow flow : List.of(new TwoWheelOrderFlow(), new TestSceneOrderFlow())) {
            for (String portStatus : List.of("IDLE", "OCCUPIED", "FAULT", "CHARGING")) {
                for (Boolean plugged : new Boolean[]{null, true, false}) {
                    for (boolean enabled : new boolean[]{true, false}) {
                        for (Long appUser : new Long[]{null, 7L}) {
                            OccupyDecision decision = flow.resolveOccupy(new OccupyContext(
                                    enabled, appUser, true, portStatus, plugged));
                            assertThat(vocabulary).contains(decision.portStatus());
                        }
                    }
                }
            }
        }
    }

    private void stubStartDependencies() {
        when(portMapper.update(isNull(), any())).thenReturn(1);
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        when(orderMapper.selectById(any())).thenReturn(pendingStartOrder());
        when(rateService.requireEnabled(STATION_ID)).thenReturn(enabledRate());
        when(memberBenefitService.evaluate(any())).thenReturn(List.<BenefitItem>of());
        when(commandService.create(any(), any(), any(), any(), any())).thenReturn(command());
    }

    private static Charger charger(String scene) {
        Charger charger = new Charger();
        charger.setId(CHARGER_ID);
        charger.setStationId(STATION_ID);
        charger.setDeviceNo("BC-S12-001");
        charger.setProtocol(BalanMqttAdapter.PROTOCOL);
        charger.setScene(scene);
        charger.setOnlineStatus("ONLINE");
        return charger;
    }

    private static ChargerPort port(String plugged) {
        ChargerPort port = new ChargerPort();
        port.setId(91L);
        port.setChargerId(CHARGER_ID);
        port.setPortNo(PORT_NO);
        port.setStatus("IDLE");
        port.setPlugged(plugged);
        return port;
    }

    private static StartChargingRequest request() {
        StartChargingRequest request = new StartChargingRequest();
        request.setMaxDurationMinutes(60);
        request.setChargeLimitAmount(new BigDecimal("20.0000"));
        return request;
    }

    private static ChargingActor actor() {
        return ChargingActor.appUser(77L, "测试用户");
    }

    private static EnabledRate enabledRate() {
        RateStrategy strategy = new RateStrategy();
        strategy.setId(5L);
        strategy.setName("测试费率");
        strategy.setBillingMode("TIME_ENERGY");
        return new EnabledRate(strategy, List.of(), null, List.of());
    }

    private static DeviceCommand command() {
        DeviceCommand command = new DeviceCommand();
        command.setCmdId("CMD-TEST-1");
        command.setType(CommandType.START.name());
        return command;
    }

    private static ChargeOrder pendingStartOrder() {
        ChargeOrder order = new ChargeOrder();
        order.setId(5L);
        order.setOrderNo("CO-TEST-5");
        order.setStationId(STATION_ID);
        order.setChargerId(CHARGER_ID);
        order.setPortNo(PORT_NO);
        order.setAppUserId(77L);
        order.setStatus(OrderStatus.PENDING_START.name());
        order.setStartCmdId("CMD-TEST-1");
        return order;
    }

    private static ChargeOrder stoppingOrder(int ageSeconds) {
        ChargeOrder order = pendingStartOrder();
        order.setStatus(OrderStatus.STOPPING.name());
        order.setStartMeter(BigDecimal.ZERO);
        order.setUpdateTime(LocalDateTime.now().minusSeconds(ageSeconds));
        return order;
    }

    /** 结算走的是 TransactionTemplate，测试里不需要真的开事务。 */
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
