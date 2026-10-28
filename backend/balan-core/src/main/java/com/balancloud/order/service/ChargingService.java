package com.balancloud.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.balancloud.command.domain.DeviceCommand;
import com.balancloud.command.event.CommandResultEvent;
import com.balancloud.command.model.CommandStatus;
import com.balancloud.command.model.CommandType;
import com.balancloud.command.service.CommandService;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.domain.StartMethod;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.protocol.DeviceProtocolAdapter;
import com.balancloud.device.protocol.PlatformEvent;
import com.balancloud.device.protocol.ProtocolAdapterRegistry;
import com.balancloud.device.protocol.ProtocolCapabilities;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.framework.tenant.TenantTaskExecutor;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.dto.StartChargingRequest;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.ChargingActor;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.order.model.PaymentStatus;
import com.balancloud.order.model.RateSnapshot;
import com.balancloud.order.model.StopReason;
import com.balancloud.order.vo.ChargingActionVo;
import com.balancloud.order.flow.AutoStopRule;
import com.balancloud.order.flow.OrderFlow;
import com.balancloud.order.flow.OrderFlowRegistry;
import com.balancloud.order.flow.StartContext;
import com.balancloud.rate.service.RateService;
import com.balancloud.rate.vo.EnabledRate;
import com.balancloud.spi.MemberBenefitService;
import com.balancloud.spi.ReservationGuard;
import com.balancloud.spi.SettlementService;
import com.balancloud.spi.dto.BenefitItem;
import com.balancloud.spi.dto.BenefitQuery;
import com.balancloud.spi.dto.SettlementContext;
import com.balancloud.spi.dto.StartGuardQuery;
import com.balancloud.system.service.MessageService;
import com.balancloud.ws.EventStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ChargingService {

    private static final Logger log = LoggerFactory.getLogger(ChargingService.class);
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicInteger ORDER_SEQUENCE = new AtomicInteger();
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final List<String> ACTIVE_STATUSES = List.of(
            OrderStatus.PENDING_START.name(), OrderStatus.CHARGING.name(), OrderStatus.STOPPING.name());
    private static final int OFFLINE_SETTLE_SECONDS = 300;
    private static final long STOP_SWEEP_SECONDS = 5;
    private static final long SETTLE_SWEEP_SECONDS = 60;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 桩已受理远程启动但一直不发 StartTransaction 的兜底时长（直流握手慢，留足 5 分钟）。 */
    private static final Duration PENDING_START_TIMEOUT = Duration.ofMinutes(5);

    private final ChargeOrderMapper orderMapper;
    private final ChargerMapper chargerMapper;
    private final ChargerPortMapper portMapper;
    private final RateService rateService;
    private final BillingService billingService;
    private final CommandService commandService;
    private final PortLockService portLockService;
    private final StationScopeService scopeService;
    private final OccupancyService occupancyService;
    private final EventStreamService eventStreamService;
    private final MessageService messageService;
    private final TenantTaskExecutor tenantTasks;
    private final ReservationGuard reservationGuard;
    private final SettlementService settlementService;
    private final MemberBenefitService memberBenefitService;
    private final OrderFlowRegistry orderFlowRegistry;
    private final ProtocolAdapterRegistry protocolAdapterRegistry;
    private final TransactionTemplate transactionTemplate;

    public ChargingService(ChargeOrderMapper orderMapper,
                           ChargerMapper chargerMapper,
                           ChargerPortMapper portMapper,
                           RateService rateService,
                           BillingService billingService,
                           CommandService commandService,
                           PortLockService portLockService,
                           StationScopeService scopeService,
                           OccupancyService occupancyService,
                           EventStreamService eventStreamService,
                           MessageService messageService,
                           TenantTaskExecutor tenantTasks,
                           ReservationGuard reservationGuard,
                           SettlementService settlementService,
                           MemberBenefitService memberBenefitService,
                           OrderFlowRegistry orderFlowRegistry,
                           ProtocolAdapterRegistry protocolAdapterRegistry,
                           PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.chargerMapper = chargerMapper;
        this.portMapper = portMapper;
        this.rateService = rateService;
        this.billingService = billingService;
        this.commandService = commandService;
        this.portLockService = portLockService;
        this.scopeService = scopeService;
        this.occupancyService = occupancyService;
        this.eventStreamService = eventStreamService;
        this.messageService = messageService;
        this.tenantTasks = tenantTasks;
        this.reservationGuard = reservationGuard;
        this.settlementService = settlementService;
        this.memberBenefitService = memberBenefitService;
        this.orderFlowRegistry = orderFlowRegistry;
        this.protocolAdapterRegistry = protocolAdapterRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public ChargingActionVo start(ChargingActor actor, Long chargerId, Integer portNo, StartChargingRequest request) {
        Charger charger = requireCharger(chargerId);
        if (actor.isOperator()) {
            scopeService.assertAccess(charger.getStationId());
        }
        if (StartMethod.isPlugAndCharge(charger.getStartMethod())) {
            throw new ServiceException(400, "该设备为插枪即充模式，插枪后由设备自动开始充电，请勿扫码启动");
        }
        if (!"ONLINE".equals(charger.getOnlineStatus())) {
            throw new ServiceException(400, "设备离线，无法启动充电");
        }
        ProtocolCapabilities capabilities = capabilitiesOf(charger);
        if (capabilities == null || !capabilities.remoteStart()) {
            throw new ServiceException(400, "当前设备协议暂不支持远程启动");
        }

        String lockToken = portLockService.lock(chargerId, portNo);
        try {
            ChargerPort port = requirePort(chargerId, portNo);
            ChargeOrder active = findActiveOrder(chargerId, portNo);
            flowOf(charger).assertStartable(new StartContext(charger.getDeviceNo(),
                    charger.getOnlineStatus(), portNo, port.getStatus(), port.getPlugged(),
                    port.getCurrentOrderNo(), active != null, capabilities));
            if (StringUtils.hasText(port.getCurrentOrderNo())) {
                throw new ServiceException(409, "端口存在未结束的订单，请稍后重试");
            }
            if (active != null) {
                throw new ServiceException(409, "端口已有进行中订单 " + active.getOrderNo());
            }
            if ("OCCUPIED".equals(port.getStatus())) {
                occupancyService.releaseForStart(chargerId, portNo);
            }
            reservationGuard.assertAllowed(new StartGuardQuery(TenantContext.get(),
                    charger.getStationId(), charger.getId(), portNo, actor.appUserId()));

            EnabledRate enabledRate = rateService.requireEnabled(charger.getStationId());
            BigDecimal limit = request.getChargeLimitAmount().setScale(4, RoundingMode.HALF_UP);
            List<BenefitItem> benefits = memberBenefitService.evaluate(new BenefitQuery(
                    actor.appUserId(), TenantContext.get(), charger.getStationId(),
                    charger.getId(), portNo, limit));

            ChargeOrder order = createOrder(charger, portNo, actor, request.getVehicleId(),
                    StartMethod.SCAN, null, enabledRate, benefits, limit,
                    request.getMaxDurationMinutes(), OrderStatus.PENDING_START.name(), null);

            int portUpdated = portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                    .eq(ChargerPort::getId, port.getId())
                    .eq(ChargerPort::getStatus, "IDLE")
                    .set(ChargerPort::getCurrentOrderNo, order.getOrderNo()));
            if (portUpdated == 0) {
                throw new ServiceException(409, "端口状态已变化，请刷新后重试");
            }

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("orderNo", order.getOrderNo());
            params.put("maxDurationMinutes", request.getMaxDurationMinutes());
            params.put("chargeLimitAmount", limit);
            DeviceCommand command = commandService.create(chargerId, portNo, CommandType.START,
                    params, actor.name());
            orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getId, order.getId())
                    .set(ChargeOrder::getStartCmdId, command.getCmdId()));
            log.info("Charging order {} created for {}/port{} by {}, cmd {}",
                    order.getOrderNo(), charger.getDeviceNo(), portNo,
                    actor.name(), command.getCmdId());
            publishOrder(order);
            return new ChargingActionVo(order.getId(), order.getOrderNo(), portNo,
                    order.getStatus(), command.getCmdId());
        } finally {
            portLockService.unlock(chargerId, portNo, lockToken);
        }
    }

    /**
     * 即插即充建单：设备已经上报 StartTransaction，订单直接进 CHARGING，没有 START 指令可发。
     *
     * <p>调用方（{@code OcppPlugAndChargeService}）负责 VIN 匹配、黑名单与限额；这里只做端口、
     * 费率与落库。任何校验失败都抛 {@link ServiceException}，由调用方转成「拒绝这笔交易」。
     */
    @Transactional
    public ChargeOrder startPlugAndCharge(Charger charger, Integer portNo, Long appUserId, Long vehicleId,
                                          String vin, StartChargingRequest request, long ts) {
        if (!StartMethod.isPlugAndCharge(charger.getStartMethod())) {
            return null;
        }
        String lockToken = portLockService.lock(charger.getId(), portNo);
        try {
            ChargerPort port = requirePort(charger.getId(), portNo);
            ChargeOrder active = findActiveOrder(charger.getId(), portNo);
            if (active != null) {
                // 重复的 StartTransaction：订单已经建好，直接复用，不再插第二笔。
                return active;
            }
            flowOf(charger).assertStartable(new StartContext(charger.getDeviceNo(),
                    charger.getOnlineStatus(), portNo, port.getStatus(), port.getPlugged(),
                    port.getCurrentOrderNo(), false, capabilitiesOf(charger)));
            if ("OCCUPIED".equals(port.getStatus())) {
                occupancyService.releaseForStart(charger.getId(), portNo);
            }

            EnabledRate enabledRate = rateService.requireEnabled(charger.getStationId());
            BigDecimal limit = request.getChargeLimitAmount().setScale(4, RoundingMode.HALF_UP);
            List<BenefitItem> benefits = memberBenefitService.evaluate(new BenefitQuery(
                    appUserId, TenantContext.get(), charger.getStationId(),
                    charger.getId(), portNo, limit));
            LocalDateTime startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE);
            ChargeOrder order = createOrder(charger, portNo, ChargingActor.device(appUserId), vehicleId,
                    StartMethod.PLUG_AND_CHARGE, vin, enabledRate, benefits, limit,
                    request.getMaxDurationMinutes(), OrderStatus.CHARGING.name(), startTime);

            int portUpdated = portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                    .eq(ChargerPort::getId, port.getId())
                    .isNull(ChargerPort::getCurrentOrderNo)
                    .set(ChargerPort::getCurrentOrderNo, order.getOrderNo())
                    .set(ChargerPort::getStatus, "CHARGING")
                    .set(ChargerPort::getPlugged, "1"));
            if (portUpdated == 0) {
                throw new ServiceException(409, "端口状态已变化，插枪即充建单失败");
            }
            log.info("Plug&charge order {} created for {}/port{} from VIN {}",
                    order.getOrderNo(), charger.getDeviceNo(), portNo, vin);
            publishOrder(order);
            return order;
        } finally {
            portLockService.unlock(charger.getId(), portNo, lockToken);
        }
    }

    @Transactional
    public ChargingActionVo stop(ChargingActor actor, Long chargerId, Integer portNo) {
        Charger charger = requireCharger(chargerId);
        if (actor.isOperator()) {
            scopeService.assertAccess(charger.getStationId());
        }
        String lockToken = portLockService.lock(chargerId, portNo);
        try {
            ChargeOrder order = findActiveOrder(chargerId, portNo);
            if (order == null) {
                throw new ServiceException(400, "端口没有进行中订单");
            }
            if (OrderStatus.PENDING_START.name().equals(order.getStatus())) {
                throw new ServiceException(400, "订单正在启动中，请稍后再停止");
            }
            if (OrderStatus.STOPPING.name().equals(order.getStatus())) {
                DeviceCommand existing = commandService.findByCmdId(order.getStopCmdId());
                return new ChargingActionVo(order.getId(), order.getOrderNo(), portNo,
                        order.getStatus(), existing == null ? null : existing.getCmdId());
            }
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getId, order.getId())
                    .eq(ChargeOrder::getStatus, OrderStatus.CHARGING.name())
                    .set(ChargeOrder::getStatus, OrderStatus.STOPPING.name())
                    .set(ChargeOrder::getStopReason, StopReason.USER_STOP.name())
                    .set(ChargeOrder::getStopMessage, actor.isOperator() ? "操作员手动停止" : "用户手动停止")
                    .set(ChargeOrder::getUpdateBy, actor.name()));
            if (updated == 0) {
                throw new ServiceException(409, "订单状态已变化，请刷新后重试");
            }
            DeviceCommand command = sendStopCommand(order, StopReason.USER_STOP);
            orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getId, order.getId())
                    .set(ChargeOrder::getStopCmdId, command.getCmdId()));
            log.info("Stop requested for order {} by {}", order.getOrderNo(), actor.name());
            return new ChargingActionVo(order.getId(), order.getOrderNo(), portNo,
                    OrderStatus.STOPPING.name(), command.getCmdId());
        } finally {
            portLockService.unlock(chargerId, portNo, lockToken);
        }
    }

    public DeviceCommand unlock(Long chargerId, Integer portNo) {
        Charger charger = requireCharger(chargerId);
        scopeService.assertAccess(charger.getStationId());
        if (!"ONLINE".equals(charger.getOnlineStatus())) {
            throw new ServiceException(400, "设备离线，无法下发解锁指令");
        }
        requirePort(chargerId, portNo);
        if (findActiveOrder(chargerId, portNo) != null) {
            throw new ServiceException(400, "端口存在进行中订单，请先停止充电");
        }
        return commandService.create(chargerId, portNo, CommandType.UNLOCK, Map.of(),
                SecurityUtils.getUsername());
    }

    public DeviceCommand setThreshold(Long chargerId, Integer portNo,
                                      BigDecimal overTempC, BigDecimal overCurrentA) {
        if (overTempC == null || overTempC.compareTo(BigDecimal.valueOf(30)) < 0
                || overTempC.compareTo(BigDecimal.valueOf(120)) > 0) {
            throw new ServiceException(400, "过温阈值应在 30~120 °C 之间");
        }
        if (overCurrentA == null || overCurrentA.compareTo(BigDecimal.ONE) < 0
                || overCurrentA.compareTo(BigDecimal.valueOf(63)) > 0) {
            throw new ServiceException(400, "过流阈值应在 1~63 A 之间");
        }
        Charger charger = requireCharger(chargerId);
        scopeService.assertAccess(charger.getStationId());
        ProtocolCapabilities capabilities = capabilitiesOf(charger);
        if (capabilities == null || !capabilities.setThreshold()) {
            throw new ServiceException(400, "当前设备协议不支持下发保护阈值");
        }
        if (!"ONLINE".equals(charger.getOnlineStatus())) {
            throw new ServiceException(400, "设备离线，无法下发保护阈值");
        }
        requirePort(chargerId, portNo);
        chargerMapper.update(null, new LambdaUpdateWrapper<Charger>()
                .eq(Charger::getId, chargerId)
                .set(Charger::getOverTempC, overTempC)
                .set(Charger::getOverCurrentA, overCurrentA));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("overTempC", overTempC);
        params.put("overCurrentA", overCurrentA);
        return commandService.create(chargerId, portNo, CommandType.SET_THRESHOLD, params,
                SecurityUtils.getUsername());
    }

    public void onMetric(Charger charger, Integer portNo, PortMetric metric, long ts) {
        ChargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getChargerId, charger.getId())
                .eq(ChargeOrder::getPortNo, portNo)
                .in(ChargeOrder::getStatus, OrderStatus.CHARGING.name(), OrderStatus.STOPPING.name())
                .orderByDesc(ChargeOrder::getId)
                .last("LIMIT 1"));
        if (order == null) {
            occupancyService.onPortMetric(charger.getId(), portNo, metric);
            return;
        }
        RateSnapshot snapshot = billingService.parseSnapshot(order.getRateSnapshotJson());
        billingService.applyMetric(order, snapshot, metric, ts);
        if (!OrderStatus.CHARGING.name().equals(order.getStatus())) {
            return;
        }
        for (AutoStopRule rule : flowOf(charger).autoStopRules(snapshot)) {
            Optional<AutoStopRule.Hit> hit = rule.evaluate(order, snapshot);
            if (hit.isPresent()) {
                beginAutoStop(order, hit.get().reason(), hit.get().detail());
                break;
            }
        }
    }

    public void handleOrderEvent(String deviceNo, PlatformEvent.OrderLifecycle event) {
        String orderNo = event.orderNo();
        if (!StringUtils.hasText(orderNo)) {
            log.warn("order_event without orderNo from {}", deviceNo);
            return;
        }
        ChargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getOrderNo, orderNo)
                .last("LIMIT 1"));
        if (order == null) {
            log.warn("order_event for unknown order {} from {}", orderNo, deviceNo);
            return;
        }
        if (isTerminal(order)) {
            return;
        }
        Charger charger = chargerMapper.selectById(order.getChargerId());
        if (charger == null || !charger.getDeviceNo().equals(deviceNo)) {
            log.warn("order_event device mismatch for {}: {}", orderNo, deviceNo);
            return;
        }
        StopReason eventReason = StopReason.parseNullable(event.stopReason());
        StopReason reason = StopReason.parseNullable(order.getStopReason());
        if (reason == null) {
            reason = eventReason == null ? StopReason.DEVICE_STOP : eventReason;
        }
        BigDecimal finalMeter = event.energyTotal() == null
                ? null
                : BigDecimal.valueOf(event.energyTotal()).setScale(4, RoundingMode.HALF_UP);
        OrderStatus terminal = reason.abnormal() ? OrderStatus.ABNORMAL : OrderStatus.COMPLETED;
        settle(order.getId(), terminal, reason, "设备上报停止事件", finalMeter);
    }

    /**
     * 设备侧真正开始充电（OCPP StartTransaction）：把 PENDING_START 推到 CHARGING。
     *
     * <p>只改订单与端口，不碰 OCPP 交易表——交易号映射由 {@code OcppTransactionBridge} 维护，
     * 避免订单服务反向依赖协议实现。
     *
     * @return 这一枪对应的进行中订单；没有平台订单时返回 null（调用方据此拒绝对端交易）
     */
    public ChargeOrder onTransactionStarted(Charger charger, Integer portNo, Double meterStartKwh, long ts) {
        ChargeOrder order = findActiveOrder(charger.getId(), portNo);
        if (order == null) {
            if (!StartMethod.isPlugAndCharge(charger.getStartMethod())) {
                log.warn("Transaction started on {} connector {} but no active order",
                        charger.getDeviceNo(), portNo);
            }
            return null;
        }
        if (!OrderStatus.PENDING_START.name().equals(order.getStatus())) {
            // 重复的 StartTransaction：订单已经在充电或已停止，保持幂等。
            return order;
        }
        LocalDateTime startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE);
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, order.getId())
                .eq(ChargeOrder::getStatus, OrderStatus.PENDING_START.name())
                .set(ChargeOrder::getStatus, OrderStatus.CHARGING.name())
                .set(ChargeOrder::getStartTime, startTime)
                .set(ChargeOrder::getUpdateBy, "device"));
        if (updated == 0) {
            return orderMapper.selectById(order.getId());
        }
        portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, order.getChargerId())
                .eq(ChargerPort::getPortNo, order.getPortNo())
                .eq(ChargerPort::getCurrentOrderNo, order.getOrderNo())
                .set(ChargerPort::getStatus, "CHARGING")
                .set(ChargerPort::getPlugged, "1"));
        log.info("Order {} enters CHARGING on device StartTransaction (meterStart {} kWh)",
                order.getOrderNo(), meterStartKwh);
        ChargeOrder latest = orderMapper.selectById(order.getId());
        publishOrder(latest);
        return latest;
    }

    /**
     * 设备侧结束交易（OCPP StopTransaction）：按设备给的最终电表结算。
     *
     * <p>已经在终态的订单直接返回，停止事件重复到达不会二次结算。
     */
    public ChargeOrder onTransactionStopped(Charger charger, Integer portNo, Double meterStopKwh,
                                            String stopReason, long ts) {
        ChargeOrder order = findActiveOrder(charger.getId(), portNo);
        if (order == null) {
            order = orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getChargerId, charger.getId())
                    .eq(ChargeOrder::getPortNo, portNo)
                    .orderByDesc(ChargeOrder::getId)
                    .last("LIMIT 1"));
            if (order == null || isTerminal(order)) {
                log.warn("StopTransaction from {} connector {} without matching order",
                        charger.getDeviceNo(), portNo);
                return order;
            }
        }
        StopReason reason = StopReason.parseNullable(order.getStopReason());
        if (reason == null) {
            reason = StopReason.parseNullable(stopReason);
        }
        if (reason == null) {
            reason = StopReason.DEVICE_STOP;
        }
        BigDecimal finalMeter = meterStopKwh == null
                ? null : BigDecimal.valueOf(meterStopKwh).setScale(4, RoundingMode.HALF_UP);
        OrderStatus terminal = reason.abnormal() ? OrderStatus.ABNORMAL : OrderStatus.COMPLETED;
        settle(order.getId(), terminal, reason, "设备上报 StopTransaction", finalMeter);
        return orderMapper.selectById(order.getId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommandResult(CommandResultEvent event) {
        DeviceCommand command = event.getCommand();
        CommandType type;
        try {
            type = CommandType.parse(command.getType());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (type == CommandType.START) {
            handleStartResult(command, event.getResult());
        } else if (type == CommandType.STOP) {
            handleStopResult(command, event.getResult());
        }
    }

    private void handleStartResult(DeviceCommand command, String result) {
        ChargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getStartCmdId, command.getCmdId())
                .last("LIMIT 1"));
        if (order == null) {
            return;
        }
        if ("SUCCESS".equals(result)) {
            OrderFlow flow = flowOf(chargerMapper.selectById(order.getChargerId()));
            if (!flow.startsOnCommandAck()) {
                // ack 只代表握手的某一步成功：真实开始时间由设备侧阶段推进，订单留在 PENDING_START。
                log.info("Order {} start ack received; scene {} waits for pre-charge phases",
                        order.getOrderNo(), flow.scene());
                return;
            }
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getId, order.getId())
                    .eq(ChargeOrder::getStatus, OrderStatus.PENDING_START.name())
                    .set(ChargeOrder::getStatus, OrderStatus.CHARGING.name())
                    .set(ChargeOrder::getStartTime, LocalDateTime.now())
                    .set(ChargeOrder::getUpdateBy, "device"));
            if (updated == 1) {
                portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                        .eq(ChargerPort::getChargerId, order.getChargerId())
                        .eq(ChargerPort::getPortNo, order.getPortNo())
                        .eq(ChargerPort::getCurrentOrderNo, order.getOrderNo())
                        .set(ChargerPort::getStatus, "CHARGING"));
                log.info("Order {} started by device ack", order.getOrderNo());
                publishOrder(orderMapper.selectById(order.getId()));
            }
            return;
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, order.getId())
                .eq(ChargeOrder::getStatus, OrderStatus.PENDING_START.name())
                .set(ChargeOrder::getStatus, OrderStatus.FAILED.name())
                .set(ChargeOrder::getEndTime, LocalDateTime.now())
                .set(ChargeOrder::getStopReason, StopReason.START_FAILED.name())
                .set(ChargeOrder::getStopMessage, truncate(command.getResultMessage()))
                .set(ChargeOrder::getUpdateBy, "device"));
        if (updated == 1) {
            clearPort(order, "IDLE");
            log.warn("Order {} start failed: {}", order.getOrderNo(), command.getResultMessage());
            messageService.publish("ORDER", "MAJOR", order.getStationId(), "充电启动失败",
                    order.getOrderNo() + " 启动失败：" + truncate(command.getResultMessage()),
                    "ORDER", order.getId());
            publishOrder(orderMapper.selectById(order.getId()));
            if (CommandStatus.TIMEOUT.name().equals(command.getStatus())) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("orderNo", order.getOrderNo());
                params.put("reason", StopReason.START_FAILED.name());
                commandService.create(order.getChargerId(), order.getPortNo(), CommandType.STOP,
                        params, "system");
            }
        }
    }

    private void handleStopResult(DeviceCommand command, String result) {
        ChargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getStopCmdId, command.getCmdId())
                .last("LIMIT 1"));
        if (order == null) {
            return;
        }
        if (isTerminal(order)) {
            return;
        }
        OrderFlow flow = flowOf(chargerMapper.selectById(order.getChargerId()));
        if (!flow.settlesOnCommandAck()) {
            // 直流桩的 RemoteStop 回执只代表「受理了」：电量以 StopTransaction.meterStop 为准，
            // 这里结算会把停止瞬间的电量漏掉。迟迟等不到停止事件的由 STOPPING 兜底扫描收敛。
            log.info("Order {} stop ack received; scene {} waits for StopTransaction",
                    order.getOrderNo(), flow.scene());
            return;
        }
        if ("SUCCESS".equals(result)) {
            StopReason reason = StopReason.parseNullable(order.getStopReason());
            settle(order.getId(), OrderStatus.COMPLETED,
                    reason == null ? StopReason.DEVICE_STOP : reason,
                    "停止指令回执成功", null);
        } else {
            settle(order.getId(), OrderStatus.ABNORMAL, StopReason.STOP_TIMEOUT,
                    "停止指令未成功：" + command.getResultMessage(), null);
        }
    }

    private void beginAutoStop(ChargeOrder order, StopReason reason, String message) {
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, order.getId())
                .eq(ChargeOrder::getStatus, OrderStatus.CHARGING.name())
                .isNull(ChargeOrder::getStopCmdId)
                .set(ChargeOrder::getStatus, OrderStatus.STOPPING.name())
                .set(ChargeOrder::getStopReason, reason.name())
                .set(ChargeOrder::getStopMessage, message)
                .set(ChargeOrder::getUpdateBy, "system"));
        if (updated == 0) {
            return;
        }
        DeviceCommand command = sendStopCommand(order, reason);
        orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, order.getId())
                .set(ChargeOrder::getStopCmdId, command.getCmdId()));
        order.setStatus(OrderStatus.STOPPING.name());
        order.setStopReason(reason.name());
        order.setStopCmdId(command.getCmdId());
        log.info("Auto stop order {}: {}", order.getOrderNo(), reason);
        publishOrder(order);
    }

    private DeviceCommand sendStopCommand(ChargeOrder order, StopReason reason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orderNo", order.getOrderNo());
        params.put("reason", reason.name());
        return commandService.create(order.getChargerId(), order.getPortNo(),
                CommandType.STOP, params, "system");
    }

    public void settle(Long orderId, OrderStatus terminal, StopReason reason,
                       String message, BigDecimal finalMeter) {
        // 订单终态与结算单必须同一时刻可见：结算单晚几毫秒落库时，紧接着的支付会更新不到它，
        // 而「支付成功」信号挂在结算单状态推进上，积分与推荐奖励会永久丢失。
        SettleResult result = transactionTemplate.execute(
                status -> settleInTransaction(orderId, terminal, reason, message, finalMeter));
        if (result == null) {
            return;
        }
        ChargeOrder latest = result.order();
        log.info("Order {} settled as {} ({})", latest.getOrderNo(), terminal, reason);
        messageService.publish("ORDER",
                terminal == OrderStatus.ABNORMAL ? "MAJOR" : "INFO",
                latest.getStationId(),
                terminal == OrderStatus.ABNORMAL ? "充电订单异常结束" : "充电订单已完成",
                latest.getOrderNo() + " 已结算，金额 " + result.total() + " 元"
                        + (reason == null ? "" : "，停止原因 " + reason.name()),
                "ORDER", latest.getId());
        publishOrder(latest);
    }

    private SettleResult settleInTransaction(Long orderId, OrderStatus terminal, StopReason reason,
                                             String message, BigDecimal finalMeter) {
        ChargeOrder order = orderMapper.selectById(orderId);
        if (order == null || !OrderStatus.parse(order.getStatus()).isActive()) {
            return null;
        }
        RateSnapshot snapshot = billingService.parseSnapshot(order.getRateSnapshotJson());
        if (finalMeter != null) {
            billingService.applyFinalMeter(order, snapshot, finalMeter);
        }
        BigDecimal endMeter = order.getEndMeter() != null ? order.getEndMeter() : order.getLastMeter();
        if (endMeter == null) {
            endMeter = order.getStartMeter();
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, orderId)
                .in(ChargeOrder::getStatus, ACTIVE_STATUSES)
                .set(ChargeOrder::getStatus, terminal.name())
                .set(ChargeOrder::getEndTime, now)
                .set(ChargeOrder::getEndMeter, endMeter)
                .set(ChargeOrder::getStopReason, reason == null ? null : reason.name())
                .set(ChargeOrder::getStopMessage, truncate(message))
                .set(ChargeOrder::getUpdateBy, "system"));
        if (updated == 0) {
            return null;
        }
        ChargeOrder latest = orderMapper.selectById(orderId);
        BigDecimal total = BillingService.money(nz(latest.getTimeFee())
                .add(nz(latest.getEnergyFee())).add(nz(latest.getServiceFee())));
        if (latest.getTotalFee() == null || latest.getTotalFee().compareTo(total) != 0) {
            orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getId, orderId)
                    .set(ChargeOrder::getTotalFee, total));
            latest.setTotalFee(total);
        }
        String portStatus = (reason == StopReason.FAULT || reason == StopReason.DEVICE_OFFLINE
                || reason == StopReason.STOP_TIMEOUT) ? "FAULT" : "IDLE";
        ChargerPort port = portMapper.selectOne(new LambdaQueryWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, latest.getChargerId())
                .eq(ChargerPort::getPortNo, latest.getPortNo())
                .last("LIMIT 1"));
        boolean occupied = "IDLE".equals(portStatus) && occupancyService.canOccupy(latest, port);
        portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, latest.getChargerId())
                .eq(ChargerPort::getPortNo, latest.getPortNo())
                .eq(ChargerPort::getCurrentOrderNo, latest.getOrderNo())
                .set(ChargerPort::getCurrentOrderNo, null)
                .set(ChargerPort::getStatus, occupied ? "OCCUPIED" : portStatus)
                .set(ChargerPort::getOccupiedSince, occupied ? now : null)
                .set(ChargerPort::getOccupancyOrderNo, occupied ? latest.getOrderNo() : null));
        try {
            settlementService.afterSettle(new SettlementContext(latest.getId(), latest.getOrderNo(),
                    TenantContext.get(), latest.getStationId(), latest.getAppUserId(), latest.getStatus(),
                    latest.getStopReason(), total, latest.getEnergyKwh(), now));
        } catch (Exception e) {
            log.warn("Settlement hook failed for {}: {}", latest.getOrderNo(), e.getMessage());
        }
        return new SettleResult(latest, total);
    }

    private record SettleResult(ChargeOrder order, BigDecimal total) {
    }

    @Scheduled(fixedDelay = STOP_SWEEP_SECONDS * 1000, initialDelay = 10000)
    public void sweepStoppingOrders() {
        tenantTasks.forEach("stop-sweep", Duration.ofSeconds(STOP_SWEEP_SECONDS),
                Duration.ofSeconds(STOP_SWEEP_SECONDS - 1), this::sweepStoppingOrdersForTenant);
    }

    private void sweepStoppingOrdersForTenant() {
        LocalDateTime now = LocalDateTime.now();
        List<ChargeOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getStatus, OrderStatus.STOPPING.name())
                .orderByAsc(ChargeOrder::getUpdateTime)
                .last("LIMIT 50"));
        for (ChargeOrder order : orders) {
            Duration timeout = flowOf(chargerMapper.selectById(order.getChargerId())).stoppingTimeout();
            if (order.getUpdateTime() != null && order.getUpdateTime().isBefore(now.minus(timeout))) {
                settle(order.getId(), OrderStatus.ABNORMAL, StopReason.STOP_TIMEOUT,
                        "停止指令 " + timeout.getSeconds() + " 秒内未收到设备最终数据", null);
            }
        }
    }

    @Scheduled(fixedDelay = SETTLE_SWEEP_SECONDS * 1000, initialDelay = 30000)
    public void sweepOfflineChargingOrders() {
        tenantTasks.forEach("offline-settle", Duration.ofSeconds(SETTLE_SWEEP_SECONDS),
                Duration.ofSeconds(SETTLE_SWEEP_SECONDS - 10), this::sweepOfflineChargingOrdersForTenant);
    }

    private void sweepOfflineChargingOrdersForTenant() {
        List<ChargeOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getStatus, OrderStatus.CHARGING.name())
                .last("LIMIT 100"));
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(OFFLINE_SETTLE_SECONDS);
        for (ChargeOrder order : orders) {
            Charger charger = chargerMapper.selectById(order.getChargerId());
            if (charger == null || !"OFFLINE".equals(charger.getOnlineStatus())) {
                continue;
            }
            LocalDateTime last = charger.getLastMetricAt() == null
                    ? charger.getLastOnlineAt() : charger.getLastMetricAt();
            if (last == null || last.isBefore(threshold)) {
                settle(order.getId(), OrderStatus.ABNORMAL, StopReason.DEVICE_OFFLINE,
                        "设备离线超过 5 分钟，按最后测点结算", null);
            }
        }
    }

    /**
     * 停在 PENDING_START 的兜底：只扫「ack 不等于开始充电」的场景（直流）。
     *
     * <p>两轮桩的指令超时由 {@code CommandService} 推进 {@code handleStartResult} 失败，不在这里重复处理；
     * 直流桩的 RemoteStart 可能回 Accepted 之后一直等不到 StartTransaction（枪没插、握手失败），
     * 这类单子不扫就会永远挂在启动中。
     */
    @Scheduled(fixedDelay = SETTLE_SWEEP_SECONDS * 1000, initialDelay = 45000)
    public void sweepPendingStartOrders() {
        tenantTasks.forEach("pending-start-sweep", Duration.ofSeconds(SETTLE_SWEEP_SECONDS),
                Duration.ofSeconds(SETTLE_SWEEP_SECONDS - 10), this::sweepPendingStartForTenant);
    }

    private void sweepPendingStartForTenant() {
        LocalDateTime threshold = LocalDateTime.now().minus(PENDING_START_TIMEOUT);
        List<ChargeOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getStatus, OrderStatus.PENDING_START.name())
                .orderByAsc(ChargeOrder::getCreateTime)
                .last("LIMIT 50"));
        for (ChargeOrder order : orders) {
            if (order.getCreateTime() == null || order.getCreateTime().isAfter(threshold)) {
                continue;
            }
            Charger charger = chargerMapper.selectById(order.getChargerId());
            if (charger == null || flowOf(charger).startsOnCommandAck()) {
                continue;
            }
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getId, order.getId())
                    .eq(ChargeOrder::getStatus, OrderStatus.PENDING_START.name())
                    .set(ChargeOrder::getStatus, OrderStatus.FAILED.name())
                    .set(ChargeOrder::getEndTime, LocalDateTime.now())
                    .set(ChargeOrder::getStopReason, StopReason.START_FAILED.name())
                    .set(ChargeOrder::getStopMessage, "超过 "
                            + PENDING_START_TIMEOUT.toMinutes() + " 分钟未收到设备 StartTransaction")
                    .set(ChargeOrder::getUpdateBy, "system"));
            if (updated == 0) {
                continue;
            }
            clearPort(order, "IDLE");
            log.warn("Order {} start timed out waiting for StartTransaction", order.getOrderNo());
            messageService.publish("ORDER", "MAJOR", order.getStationId(), "充电启动失败",
                    order.getOrderNo() + " 启动超时：设备未上报 StartTransaction",
                    "ORDER", order.getId());
            publishOrder(orderMapper.selectById(order.getId()));
        }
    }

    private boolean isTerminal(ChargeOrder order) {
        try {
            return OrderStatus.parse(order.getStatus()).isTerminal();
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private void clearPort(ChargeOrder order, String status) {
        portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, order.getChargerId())
                .eq(ChargerPort::getPortNo, order.getPortNo())
                .eq(ChargerPort::getCurrentOrderNo, order.getOrderNo())
                .set(ChargerPort::getCurrentOrderNo, null)
                .set(ChargerPort::getStatus, status));
    }

    public ChargeOrder findActiveByUser(Long appUserId) {
        return orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getAppUserId, appUserId)
                .in(ChargeOrder::getStatus, ACTIVE_STATUSES)
                .orderByDesc(ChargeOrder::getId)
                .last("LIMIT 1"));
    }

    private ChargeOrder findActiveOrder(Long chargerId, Integer portNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getChargerId, chargerId)
                .eq(ChargeOrder::getPortNo, portNo)
                .in(ChargeOrder::getStatus, ACTIVE_STATUSES)
                .orderByDesc(ChargeOrder::getId)
                .last("LIMIT 1"));
    }

    private Charger requireCharger(Long chargerId) {
        Charger charger = chargerMapper.selectById(chargerId);
        if (charger == null) {
            throw new ServiceException(404, "设备不存在");
        }
        return charger;
    }

    private OrderFlow flowOf(Charger charger) {
        return orderFlowRegistry.require(charger == null ? null : charger.getScene());
    }

    /** 未注册的协议返回 null，让启动校验走「暂不支持远程启动」的 400，而不是把脏数据变成 500。 */
    private ProtocolCapabilities capabilitiesOf(Charger charger) {
        return protocolAdapterRegistry.find(charger.getProtocol())
                .map(DeviceProtocolAdapter::capabilities)
                .orElse(null);
    }

    private ChargerPort requirePort(Long chargerId, Integer portNo) {
        if (portNo == null) {
            throw new ServiceException(400, "端口号不能为空");
        }
        ChargerPort port = portMapper.selectOne(new LambdaQueryWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, chargerId)
                .eq(ChargerPort::getPortNo, portNo)
                .last("LIMIT 1"));
        if (port == null) {
            throw new ServiceException(404, "端口不存在");
        }
        return port;
    }

    /**
     * 扫码启动与即插即充共用的建单落库：两者只差启动方式、初始状态和开始时间。
     * 不做任何校验，调用方负责端口锁与前置条件。
     */
    private ChargeOrder createOrder(Charger charger, Integer portNo, ChargingActor actor, Long vehicleId,
                                    String startMethod, String vin, EnabledRate enabledRate,
                                    List<BenefitItem> benefits, BigDecimal limit,
                                    Integer maxDurationMinutes, String status, LocalDateTime startTime) {
        ChargeOrder order = new ChargeOrder();
        order.setOrderNo(nextOrderNo());
        order.setStationId(charger.getStationId());
        order.setChargerId(charger.getId());
        order.setPortNo(portNo);
        order.setAppUserId(actor.appUserId());
        order.setVehicleId(actor.isOperator() ? null : vehicleId);
        order.setRateStrategyId(enabledRate.getStrategy().getId());
        order.setRateStrategyName(enabledRate.getStrategy().getName());
        order.setBillingMode(enabledRate.getStrategy().getBillingMode());
        order.setRateSnapshotJson(billingService.snapshotJson(enabledRate, benefits));
        order.setDurationSeconds(0);
        order.setEnergyKwh(ZERO);
        order.setTimeFee(ZERO);
        order.setEnergyFee(ZERO);
        order.setServiceFee(ZERO);
        order.setTotalFee(ZERO);
        order.setMaxDurationMinutes(maxDurationMinutes);
        order.setChargeLimitAmount(limit);
        order.setStatus(status);
        order.setStartTime(startTime);
        order.setPaymentStatus(PaymentStatus.UNPAID.name());
        order.setStartMethod(startMethod);
        order.setVin(vin);
        order.setCreateBy(actor.name());
        orderMapper.insert(order);
        return order;
    }

    private String nextOrderNo() {
        String suffix = String.format("%03d", Math.floorMod(ORDER_SEQUENCE.getAndIncrement(), 1000));
        return "CO" + LocalDateTime.now().format(ORDER_TIME) + suffix;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private void publishOrder(ChargeOrder order) {
        if (order == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("stationId", order.getStationId());
        data.put("chargerId", order.getChargerId());
        Charger charger = chargerMapper.selectById(order.getChargerId());
        data.put("deviceNo", charger == null ? null : charger.getDeviceNo());
        data.put("portNo", order.getPortNo());
        data.put("status", order.getStatus());
        data.put("stopReason", order.getStopReason());
        data.put("energyKwh", order.getEnergyKwh());
        data.put("totalFee", order.getTotalFee());
        data.put("durationSeconds", order.getDurationSeconds());
        data.put("endTime", order.getEndTime());
        eventStreamService.publishAfterCommit("order", order.getStationId(), data);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }
}
