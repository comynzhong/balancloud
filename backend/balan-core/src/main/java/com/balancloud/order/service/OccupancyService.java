package com.balancloud.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.framework.tenant.TenantTaskExecutor;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.flow.OccupyContext;
import com.balancloud.order.flow.OccupyDecision;
import com.balancloud.order.flow.OrderFlow;
import com.balancloud.order.flow.OrderFlowRegistry;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.PaymentStatus;
import com.balancloud.system.service.SystemConfigService;
import com.balancloud.ws.EventStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 占位费（core）：订单结束后设备仍插枪时端口进入 OCCUPIED，宽限期后按小时计费。
 *
 * <p>结束信号有两个：设备上报拔枪（plugged=false）或同一端口发起新订单。
 * 故障、离线与启动失败不产生占用。费用封顶取系统配置，0 表示不封顶。
 */
@Service
public class OccupancyService {

    private static final Logger log = LoggerFactory.getLogger(OccupancyService.class);
    private static final String PORT_OCCUPIED = "OCCUPIED";
    private static final long SWEEP_SECONDS = 30;
    private static final BigDecimal ZERO4 =
            BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final ChargeOrderMapper orderMapper;
    private final ChargerPortMapper portMapper;
    private final ChargerMapper chargerMapper;
    private final SystemConfigService configService;
    private final EventStreamService eventStreamService;
    private final TenantTaskExecutor tenantTasks;
    private final OrderFlowRegistry orderFlowRegistry;

    public OccupancyService(ChargeOrderMapper orderMapper,
                            ChargerPortMapper portMapper,
                            ChargerMapper chargerMapper,
                            SystemConfigService configService,
                            EventStreamService eventStreamService,
                            TenantTaskExecutor tenantTasks,
                            OrderFlowRegistry orderFlowRegistry) {
        this.orderMapper = orderMapper;
        this.portMapper = portMapper;
        this.chargerMapper = chargerMapper;
        this.configService = configService;
        this.eventStreamService = eventStreamService;
        this.tenantTasks = tenantTasks;
        this.orderFlowRegistry = orderFlowRegistry;
    }

    public boolean isEnabled() {
        return configService.isOccupancyEnabled();
    }

    /** 订单结束且仍插枪、属于小程序用户时，端口转为占用。 */
    public boolean canOccupy(ChargeOrder order, ChargerPort port) {
        if (order == null) {
            return false;
        }
        return OccupyDecision.OCCUPIED.equals(decide(order, port, OccupyDecision.IDLE).portStatus());
    }

    /**
     * 场景决定「这一单结束后车还算不算占着位」。{@code portStatus} 由调用方给：
     * 结算时传目标状态（故障类停止传 FAULT，其余传 IDLE），拔枪测点传端口当前状态。
     */
    public OccupyDecision decide(ChargeOrder order, ChargerPort port, String portStatus) {
        Charger charger = order == null || order.getChargerId() == null
                ? null : chargerMapper.selectById(order.getChargerId());
        return flowOf(charger).resolveOccupy(new OccupyContext(isEnabled(),
                order == null ? null : order.getAppUserId(),
                port != null,
                portStatus,
                port == null ? null : Boolean.valueOf("1".equals(port.getPlugged()))));
    }

    private OrderFlow flowOf(Charger charger) {
        return orderFlowRegistry.require(charger == null ? null : charger.getScene());
    }

    public boolean isPortOccupiedFor(ChargeOrder order) {
        if (order == null || order.getOrderNo() == null) {
            return false;
        }
        ChargerPort port = findPort(order.getChargerId(), order.getPortNo());
        return port != null
                && PORT_OCCUPIED.equals(port.getStatus())
                && order.getOrderNo().equals(port.getOccupancyOrderNo());
    }

    public void markOccupied(ChargeOrder order, LocalDateTime since) {
        portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, order.getChargerId())
                .eq(ChargerPort::getPortNo, order.getPortNo())
                .set(ChargerPort::getStatus, PORT_OCCUPIED)
                .set(ChargerPort::getOccupiedSince, since)
                .set(ChargerPort::getOccupancyOrderNo, order.getOrderNo()));
        publish(order);
    }

    /** 新订单启动前结算并释放旧订单的占用，端口回到 IDLE。 */
    public void releaseForStart(Long chargerId, Integer portNo) {
        ChargerPort port = findPort(chargerId, portNo);
        if (port == null || !PORT_OCCUPIED.equals(port.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        accrue(port, now);
        portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                .eq(ChargerPort::getId, port.getId())
                .set(ChargerPort::getStatus, "IDLE")
                .set(ChargerPort::getOccupiedSince, null)
                .set(ChargerPort::getOccupancyOrderNo, null));
        log.info("Occupancy released before new order on charger {}/port{}", chargerId, portNo);
    }

    /** 设备测点到达：拔枪立即结算，未拔枪时刷新累计费用。 */
    public void onPortMetric(Long chargerId, Integer portNo, PortMetric metric) {
        if (metric == null || metric.getPlugged() == null) {
            return;
        }
        ChargerPort port = findPort(chargerId, portNo);
        if (port == null || !PORT_OCCUPIED.equals(port.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        OccupyDecision decision = flowOf(chargerMapper.selectById(chargerId))
                .resolveOccupy(new OccupyContext(isEnabled(), null, true,
                        port.getStatus(), metric.getPlugged()));
        if (!OccupyDecision.OCCUPIED.equals(decision.portStatus())) {
            if (decision.settleFirst()) {
                accrue(port, now);
            }
            portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                    .eq(ChargerPort::getId, port.getId())
                    .set(ChargerPort::getStatus, decision.portStatus())
                    .set(ChargerPort::getPlugged, "0")
                    .set(ChargerPort::getOccupiedSince, null)
                    .set(ChargerPort::getOccupancyOrderNo, null));
            log.info("Occupancy ended by unplug on charger {}/port{}", chargerId, portNo);
            return;
        }
        accrue(port, now);
    }

    /** 兜底扫描：设备长时间不上报时也能让后台看到累计占位费。 */
    @Scheduled(fixedDelay = SWEEP_SECONDS * 1000, initialDelay = 20_000)
    public void sweepOccupancy() {
        tenantTasks.forEach("occupancy-sweep", Duration.ofSeconds(SWEEP_SECONDS),
                Duration.ofSeconds(SWEEP_SECONDS - 5), this::sweepOccupancyForTenant);
    }

    private void sweepOccupancyForTenant() {
        if (!isEnabled()) {
            return;
        }
        List<ChargerPort> ports = portMapper.selectList(new LambdaQueryWrapper<ChargerPort>()
                .eq(ChargerPort::getStatus, PORT_OCCUPIED)
                .isNotNull(ChargerPort::getOccupancyOrderNo)
                .last("LIMIT 200"));
        LocalDateTime now = LocalDateTime.now();
        for (ChargerPort port : ports) {
            accrue(port, now);
        }
    }

    private void accrue(ChargerPort port, LocalDateTime now) {
        if (port.getOccupiedSince() == null || port.getOccupancyOrderNo() == null) {
            return;
        }
        ChargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getOrderNo, port.getOccupancyOrderNo())
                .last("LIMIT 1"));
        if (order == null || PaymentStatus.REFUNDED.name().equals(order.getPaymentStatus())) {
            clearPort(port);
            return;
        }
        long seconds = Math.max(0, Duration.between(port.getOccupiedSince(), now).getSeconds());
        int graceSeconds = Math.max(0, configService.getOccupancyGraceMinutes()) * 60;
        long billableSeconds = Math.max(0, seconds - graceSeconds);
        BigDecimal price = configService.getOccupancyPricePerHour();
        BigDecimal fee = price.multiply(BigDecimal.valueOf(billableSeconds))
                .divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
        BigDecimal maxFee = configService.getOccupancyMaxFee();
        if (maxFee.compareTo(BigDecimal.ZERO) > 0 && fee.compareTo(maxFee) > 0) {
            fee = maxFee.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal total = BillingService.money(nz(order.getTimeFee())
                .add(nz(order.getEnergyFee()))
                .add(nz(order.getServiceFee()))
                .add(fee));
        boolean changed = !fee.equals(order.getOccupancyFee())
                || !java.util.Objects.equals((int) seconds, order.getOccupancySeconds())
                || !total.equals(order.getTotalFee());
        if (!changed) {
            return;
        }
        orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, order.getId())
                .set(ChargeOrder::getOccupancySeconds, (int) seconds)
                .set(ChargeOrder::getOccupancyFee, fee)
                .set(ChargeOrder::getTotalFee, total)
                .set(ChargeOrder::getUpdateBy, "system"));
        order.setOccupancySeconds((int) seconds);
        order.setOccupancyFee(fee);
        order.setTotalFee(total);
        publish(order);
    }

    private void clearPort(ChargerPort port) {
        portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                .eq(ChargerPort::getId, port.getId())
                .set(ChargerPort::getStatus, "IDLE")
                .set(ChargerPort::getOccupiedSince, null)
                .set(ChargerPort::getOccupancyOrderNo, null));
    }

    private ChargerPort findPort(Long chargerId, Integer portNo) {
        if (chargerId == null || portNo == null) {
            return null;
        }
        return portMapper.selectOne(new LambdaQueryWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, chargerId)
                .eq(ChargerPort::getPortNo, portNo)
                .last("LIMIT 1"));
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO4 : value;
    }

    private void publish(ChargeOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("stationId", order.getStationId());
        data.put("chargerId", order.getChargerId());
        Charger charger = chargerMapper.selectById(order.getChargerId());
        data.put("deviceNo", charger == null ? null : charger.getDeviceNo());
        data.put("portNo", order.getPortNo());
        data.put("status", order.getStatus());
        data.put("occupancySeconds", order.getOccupancySeconds());
        data.put("occupancyFee", order.getOccupancyFee());
        data.put("totalFee", order.getTotalFee());
        eventStreamService.publishAfterCommit("order", order.getStationId(), data);
    }
}
