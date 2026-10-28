package com.balancloud.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.app.security.AppLoginUser;
import com.balancloud.app.security.AppSecurityUtils;
import com.balancloud.app.domain.AppVehicle;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.balancloud.app.vo.AppOrderDetailVo;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.domain.OrderRateDetail;
import com.balancloud.order.domain.OrderRefund;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.mapper.OrderRateDetailMapper;
import com.balancloud.order.mapper.OrderRefundMapper;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.order.model.PaymentStatus;
import com.balancloud.order.service.BillingService;
import com.balancloud.order.service.OccupancyService;
import com.balancloud.order.service.PaymentGateway;
import com.balancloud.spi.dto.PayResult;
import com.balancloud.order.vo.OrderRateDetailVo;
import com.balancloud.order.vo.OrderRefundVo;
import com.balancloud.order.vo.OrderVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AppOrderService {

    private static final List<String> ACTIVE_STATUSES = List.of(
            OrderStatus.PENDING_START.name(), OrderStatus.CHARGING.name(), OrderStatus.STOPPING.name());

    private final ChargeOrderMapper orderMapper;
    private final OrderRateDetailMapper detailMapper;
    private final ChargerMapper chargerMapper;
    private final StationMapper stationMapper;
    private final BillingService billingService;
    private final AppStationService stationService;
    private final AppVehicleMapper vehicleMapper;
    private final OrderRefundMapper refundMapper;
    private final PaymentGateway paymentGateway;
    private final OccupancyService occupancyService;

    public AppOrderService(ChargeOrderMapper orderMapper,
                           OrderRateDetailMapper detailMapper,
                           ChargerMapper chargerMapper,
                           StationMapper stationMapper,
                           BillingService billingService,
                           AppStationService stationService,
                           AppVehicleMapper vehicleMapper,
                           OrderRefundMapper refundMapper,
                           PaymentGateway paymentGateway,
                           OccupancyService occupancyService) {
        this.orderMapper = orderMapper;
        this.detailMapper = detailMapper;
        this.chargerMapper = chargerMapper;
        this.stationMapper = stationMapper;
        this.billingService = billingService;
        this.stationService = stationService;
        this.vehicleMapper = vehicleMapper;
        this.refundMapper = refundMapper;
        this.paymentGateway = paymentGateway;
        this.occupancyService = occupancyService;
    }

    public TableDataInfo list(String status, long pageNum, long pageSize) {
        Long userId = AppSecurityUtils.currentUserId();
        LambdaQueryWrapper<ChargeOrder> wrapper = new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getAppUserId, userId);
        if (StringUtils.hasText(status)) {
            String value = status.trim();
            if ("ACTIVE".equalsIgnoreCase(value)) {
                wrapper.in(ChargeOrder::getStatus, ACTIVE_STATUSES);
            } else if ("FINISHED".equalsIgnoreCase(value)) {
                wrapper.notIn(ChargeOrder::getStatus, ACTIVE_STATUSES);
            } else {
                wrapper.eq(ChargeOrder::getStatus, value);
            }
        }
        wrapper.orderByDesc(ChargeOrder::getId);
        Page<ChargeOrder> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 50));
        Page<ChargeOrder> result = orderMapper.selectPage(page, wrapper);
        return TableDataInfo.of(result.getTotal(), toVos(result.getRecords()));
    }

    public AppOrderDetailVo active() {
        ChargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getAppUserId, AppSecurityUtils.currentUserId())
                .in(ChargeOrder::getStatus, ACTIVE_STATUSES)
                .orderByDesc(ChargeOrder::getId)
                .last("LIMIT 1"));
        return order == null ? null : detail(order.getId());
    }

    public AppOrderDetailVo detail(Long id) {
        ChargeOrder order = requireOwnedOrder(id);
        AppOrderDetailVo vo = new AppOrderDetailVo();
        fill(vo, order);
        List<OrderRateDetail> details = detailMapper.selectList(new LambdaQueryWrapper<OrderRateDetail>()
                .eq(OrderRateDetail::getOrderId, id));
        vo.setDetails(details.stream()
                .sorted(Comparator.comparingInt(item -> periodOrder(item.getPeriodType())))
                .map(this::toDetailVo)
                .toList());
        vo.setRateSnapshot(billingService.parseSnapshot(order.getRateSnapshotJson()));
        OrderRefund refund = refundMapper.selectOne(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getOrderId, id)
                .last("LIMIT 1"));
        if (refund != null) {
            OrderRefundVo refundVo = new OrderRefundVo();
            BeanUtils.copyProperties(refund, refundVo);
            vo.setRefund(refundVo);
            vo.setRefundNo(refund.getRefundNo());
            vo.setRefundAmount(refund.getAmount());
            vo.setRefundReason(refund.getReason());
            vo.setRefundTime(refund.getRefundTime());
        }
        if (OrderStatus.parse(order.getStatus()).isActive()) {
            stationService.portsOf(order.getChargerId()).stream()
                    .filter(port -> port.getPortNo().equals(order.getPortNo()))
                    .findFirst()
                    .ifPresent(vo::setLive);
        }
        return vo;
    }

    @Transactional
    public AppOrderDetailVo pay(Long id) {
        AppLoginUser user = AppSecurityUtils.currentUser();
        ChargeOrder order = requireOwnedOrder(id);
        if (PaymentStatus.REFUNDED.name().equals(order.getPaymentStatus())) {
            throw new ServiceException(400, "订单已退款，不能再次支付");
        }
        if (PaymentStatus.PAID.name().equals(order.getPaymentStatus())) {
            return detail(id);
        }
        if (occupancyService.isPortOccupiedFor(order)) {
            throw new ServiceException(400, "订单仍处于占用状态，请先拔枪结算占位费");
        }
        OrderStatus status = OrderStatus.parse(order.getStatus());
        if (status == OrderStatus.FAILED) {
            throw new ServiceException(400, "启动失败的订单无需支付");
        }
        if (status.isActive()) {
            throw new ServiceException(400, "订单尚未结束，不能支付");
        }
        PayResult result = paymentGateway.pay(order, user.getNickname());
        if (!result.success()) {
            throw new ServiceException(502, result.message());
        }
        if (result.pending()) {
            // 真实通道只创建预支付：订单仍是 UNPAID，等回调或主动查单确认后再置 PAID
            AppOrderDetailVo vo = detail(id);
            vo.setPayParams(result.payload());
            return vo;
        }
        markPaid(id, user.getNickname(), result);
        return detail(id);
    }

    /** mock 同步成功才直接置 PAID；预支付（pending）必须由回调/查单推进，这里不碰订单。 */
    void markPaid(Long id, String operator, PayResult result) {
        if (result.pending()) {
            return;
        }
        orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, id)
                .eq(ChargeOrder::getPaymentStatus, PaymentStatus.UNPAID.name())
                .set(ChargeOrder::getPaymentStatus, PaymentStatus.PAID.name())
                .set(ChargeOrder::getPaidAt, LocalDateTime.now())
                .set(ChargeOrder::getUpdateBy, operator));
    }

    private ChargeOrder requireOwnedOrder(Long id) {
        ChargeOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        if (order.getAppUserId() == null || !order.getAppUserId().equals(AppSecurityUtils.currentUserId())) {
            throw new ServiceException(403, "无权访问该订单");
        }
        return order;
    }

    private List<OrderVo> toVos(List<ChargeOrder> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Charger> chargers = chargerMapper.selectBatchIds(
                        orders.stream().map(ChargeOrder::getChargerId).distinct().toList())
                .stream().collect(Collectors.toMap(Charger::getId, item -> item));
        Map<Long, Station> stations = stationMapper.selectBatchIds(
                        orders.stream().map(ChargeOrder::getStationId).distinct().toList())
                .stream().collect(Collectors.toMap(Station::getId, item -> item));
        Map<Long, AppVehicle> vehicles = loadVehicles(orders);
        return orders.stream().map(order -> {
            OrderVo vo = new OrderVo();
            BeanUtils.copyProperties(order, vo);
            Charger charger = chargers.get(order.getChargerId());
            if (charger != null) {
                vo.setDeviceNo(charger.getDeviceNo());
                vo.setDeviceName(charger.getName());
            }
            Station station = stations.get(order.getStationId());
            if (station != null) {
                vo.setStationName(station.getName());
            }
            AppVehicle vehicle = vehicles.get(order.getVehicleId());
            if (vehicle != null) {
                vo.setVehiclePlateNo(vehicle.getPlateNo());
            }
            return vo;
        }).toList();
    }

    private Map<Long, AppVehicle> loadVehicles(List<ChargeOrder> orders) {
        List<Long> ids = orders.stream().map(ChargeOrder::getVehicleId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return vehicleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AppVehicle::getId, item -> item));
    }

    private void fill(AppOrderDetailVo vo, ChargeOrder order) {
        BeanUtils.copyProperties(order, vo);
        Charger charger = chargerMapper.selectById(order.getChargerId());
        if (charger != null) {
            vo.setDeviceNo(charger.getDeviceNo());
            vo.setDeviceName(charger.getName());
        }
        Station station = stationMapper.selectById(order.getStationId());
        if (station != null) {
            vo.setStationName(station.getName());
        }
        AppVehicle vehicle = order.getVehicleId() == null ? null
                : vehicleMapper.selectById(order.getVehicleId());
        if (vehicle != null) {
            vo.setVehiclePlateNo(vehicle.getPlateNo());
        }
    }

    private OrderRateDetailVo toDetailVo(OrderRateDetail detail) {
        OrderRateDetailVo vo = new OrderRateDetailVo();
        BeanUtils.copyProperties(detail, vo);
        return vo;
    }

    private int periodOrder(String periodType) {
        return switch (periodType == null ? "" : periodType) {
            case "PEAK" -> 0;
            case "FLAT" -> 1;
            case "VALLEY" -> 2;
            default -> 3;
        };
    }
}
