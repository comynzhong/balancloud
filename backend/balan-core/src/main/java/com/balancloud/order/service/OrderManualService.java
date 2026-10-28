package com.balancloud.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.balancloud.command.domain.DeviceCommand;
import com.balancloud.command.model.CommandType;
import com.balancloud.command.service.CommandService;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.domain.OrderManualLog;
import com.balancloud.order.dto.AmountCorrectRequest;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.mapper.OrderManualLogMapper;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.order.model.PaymentStatus;
import com.balancloud.order.model.StopReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常订单人工闭环：强制结束按最后测点结算并尽量下发停机，金额修正保留前后金额与原因。
 */
@Service
public class OrderManualService {

    private static final int MIN_REASON_LENGTH = 4;

    private final ChargeOrderMapper orderMapper;
    private final OrderManualLogMapper manualLogMapper;
    private final ChargerMapper chargerMapper;
    private final CommandService commandService;
    private final ChargingService chargingService;
    private final StationScopeService scopeService;

    public OrderManualService(ChargeOrderMapper orderMapper,
                              OrderManualLogMapper manualLogMapper,
                              ChargerMapper chargerMapper,
                              CommandService commandService,
                              ChargingService chargingService,
                              StationScopeService scopeService) {
        this.orderMapper = orderMapper;
        this.manualLogMapper = manualLogMapper;
        this.chargerMapper = chargerMapper;
        this.commandService = commandService;
        this.chargingService = chargingService;
        this.scopeService = scopeService;
    }

    @Transactional
    public Map<String, Object> forceFinish(Long orderId, String reason) {
        String note = requireReason(reason);
        ChargeOrder order = requireOrder(orderId);
        OrderStatus status = OrderStatus.parse(order.getStatus());
        if (status.isTerminal()) {
            throw new ServiceException(400, "订单已结束，无需强制结束");
        }
        Charger charger = chargerMapper.selectById(order.getChargerId());
        boolean online = charger != null && "ONLINE".equals(charger.getOnlineStatus());
        String operator = SecurityUtils.getUsername();

        if (online && order.getStopCmdId() == null) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("orderNo", order.getOrderNo());
            params.put("reason", StopReason.MANUAL_FORCE.name());
            DeviceCommand command = commandService.create(order.getChargerId(), order.getPortNo(),
                    CommandType.STOP, params, operator);
            orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                    .eq(ChargeOrder::getId, orderId)
                    .set(ChargeOrder::getStopCmdId, command.getCmdId()));
        }

        boolean charging = status != OrderStatus.PENDING_START;
        OrderStatus terminal = charging
                ? (online ? OrderStatus.COMPLETED : OrderStatus.ABNORMAL)
                : OrderStatus.FAILED;
        StopReason settleReason = online ? StopReason.MANUAL_FORCE : StopReason.DEVICE_OFFLINE;
        String message = "管理员强制结束：" + note + (online ? "（已下发停机）" : "（设备离线，按最后测点结算）");
        chargingService.settle(orderId, terminal, settleReason, message, null);

        ChargeOrder latest = orderMapper.selectById(orderId);
        writeLog(latest, "FORCE_FINISH", note, order.getStatus(), latest.getStatus(),
                order.getTotalFee(), latest.getTotalFee(), operator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", latest.getId());
        result.put("orderNo", latest.getOrderNo());
        result.put("status", latest.getStatus());
        result.put("stopReason", latest.getStopReason());
        result.put("totalFee", latest.getTotalFee());
        result.put("deviceOnline", online);
        result.put("message", message);
        return result;
    }

    @Transactional
    public Map<String, Object> correctAmount(Long orderId, AmountCorrectRequest request) {
        String note = requireReason(request == null ? null : request.getReason());
        ChargeOrder order = requireOrder(orderId);
        OrderStatus status = OrderStatus.parse(order.getStatus());
        if (status.isActive()) {
            throw new ServiceException(400, "订单进行中，请先结束订单再修正金额");
        }
        if (status == OrderStatus.FAILED) {
            throw new ServiceException(400, "启动失败的订单无需修正金额");
        }
        if (PaymentStatus.REFUNDED.name().equals(order.getPaymentStatus())) {
            throw new ServiceException(409, "订单已退款，不能再修正金额");
        }
        BigDecimal beforeTotal = order.getTotalFee();
        BigDecimal timeFee = request.getTimeFee() == null ? nz(order.getTimeFee()) : request.getTimeFee();
        BigDecimal energyFee = request.getEnergyFee() == null ? nz(order.getEnergyFee()) : request.getEnergyFee();
        BigDecimal serviceFee = request.getServiceFee() == null ? nz(order.getServiceFee()) : request.getServiceFee();
        BigDecimal total = BillingService.money(timeFee.add(energyFee).add(serviceFee)
                .add(nz(order.getOccupancyFee())));
        String operator = SecurityUtils.getUsername();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, orderId)
                .set(ChargeOrder::getTimeFee, BillingService.money(timeFee))
                .set(ChargeOrder::getEnergyFee, BillingService.money(energyFee))
                .set(ChargeOrder::getServiceFee, BillingService.money(serviceFee))
                .set(ChargeOrder::getTotalFee, total)
                .set(ChargeOrder::getUpdateBy, operator));
        if (updated == 0) {
            throw new ServiceException(409, "订单状态已变化，请刷新后重试");
        }
        ChargeOrder latest = orderMapper.selectById(orderId);
        writeLog(latest, "CORRECT_AMOUNT", note, order.getStatus(), latest.getStatus(),
                beforeTotal, latest.getTotalFee(), operator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", latest.getId());
        result.put("orderNo", latest.getOrderNo());
        result.put("timeFee", latest.getTimeFee());
        result.put("energyFee", latest.getEnergyFee());
        result.put("serviceFee", latest.getServiceFee());
        result.put("totalFee", latest.getTotalFee());
        return result;
    }

    public List<OrderManualLog> listLogs(Long orderId) {
        requireOrder(orderId);
        return manualLogMapper.selectList(new LambdaQueryWrapper<OrderManualLog>()
                .eq(OrderManualLog::getOrderId, orderId)
                .orderByDesc(OrderManualLog::getId)
                .last("LIMIT 50"));
    }

    private ChargeOrder requireOrder(Long orderId) {
        ChargeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        scopeService.assertAccess(order.getStationId());
        return order;
    }

    private String requireReason(String reason) {
        String value = StringUtils.hasText(reason) ? reason.trim() : "";
        if (value.length() < MIN_REASON_LENGTH) {
            throw new ServiceException(400, "人工处理原因至少 4 个字符");
        }
        if (value.length() > 200) {
            throw new ServiceException(400, "人工处理原因不能超过 200 个字符");
        }
        return value;
    }

    private void writeLog(ChargeOrder order, String actionType, String reason,
                          String beforeStatus, String afterStatus,
                          BigDecimal beforeAmount, BigDecimal afterAmount, String operator) {
        OrderManualLog log = new OrderManualLog();
        log.setOrderId(order.getId());
        log.setOrderNo(order.getOrderNo());
        log.setActionType(actionType);
        log.setReason(reason);
        log.setBeforeStatus(beforeStatus);
        log.setAfterStatus(afterStatus);
        log.setBeforeAmount(beforeAmount);
        log.setAfterAmount(afterAmount);
        log.setOperator(operator);
        log.setCreateTime(LocalDateTime.now());
        manualLogMapper.insert(log);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
