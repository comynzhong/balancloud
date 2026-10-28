package com.balancloud.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.domain.OrderRateDetail;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.mapper.OrderRateDetailMapper;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.order.model.RatePeriodSnapshot;
import com.balancloud.order.model.RatePlanSnapshot;
import com.balancloud.order.model.RateSnapshot;
import com.balancloud.rate.model.BillingMode;
import com.balancloud.rate.model.ServiceUnit;
import com.balancloud.rate.vo.EnabledRate;
import com.balancloud.rate.vo.RatePlan;
import com.balancloud.spi.dto.BenefitItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_TICK_SECONDS = 300;
    private static final BigDecimal METER_ROLLBACK_TOLERANCE = new BigDecimal("0.1");

    private final ChargeOrderMapper orderMapper;
    private final OrderRateDetailMapper detailMapper;
    private final ObjectMapper objectMapper;

    public BillingService(ChargeOrderMapper orderMapper,
                          OrderRateDetailMapper detailMapper,
                          ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.detailMapper = detailMapper;
        this.objectMapper = objectMapper;
    }

    public String snapshotJson(EnabledRate enabledRate) {
        return snapshotJson(enabledRate, List.of());
    }

    /** benefits 来自 MemberBenefitService SPI；社区版为空，快照 JSON 与拆分前一致。 */
    public String snapshotJson(EnabledRate enabledRate, List<BenefitItem> benefits) {
        RateSnapshot snapshot = new RateSnapshot();
        snapshot.setStrategyId(enabledRate.getStrategy().getId());
        snapshot.setStrategyName(enabledRate.getStrategy().getName());
        snapshot.setBillingMode(enabledRate.getStrategy().getBillingMode());
        snapshot.setPeriods(toPeriodSnapshots(enabledRate.getPeriods()));
        snapshot.setPlans(enabledRate.getPlans().stream().map(plan -> {
            RatePlanSnapshot item = new RatePlanSnapshot();
            item.setStrategyId(plan.getStrategy().getId());
            item.setStrategyName(plan.getStrategy().getName());
            if (plan.getServiceStrategy() != null) {
                item.setServiceStrategyId(plan.getServiceStrategy().getId());
                item.setServiceStrategyName(plan.getServiceStrategy().getName());
            }
            item.setBillingMode(plan.getStrategy().getBillingMode());
            item.setEffectiveFrom(plan.getEffectiveFrom());
            item.setEffectiveTo(plan.getEffectiveTo());
            item.setPeriods(toPeriodSnapshots(plan.getPeriods()));
            return item;
        }).toList());
        if (benefits != null && !benefits.isEmpty()) {
            snapshot.setBenefits(benefits);
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new ServiceException(500, "生成费率快照失败");
        }
    }

    private List<RatePeriodSnapshot> toPeriodSnapshots(List<com.balancloud.rate.domain.RatePeriod> periods) {
        return periods.stream().map(period -> {
            RatePeriodSnapshot item = new RatePeriodSnapshot();
            item.setPeriodType(period.getPeriodType());
            item.setStartTime(period.getStartTime().format(HH_MM));
            item.setEndTime(period.getEndTime().format(HH_MM));
            item.setTimePrice(period.getTimePrice());
            item.setEnergyPrice(period.getEnergyPrice());
            item.setServicePrice(period.getServicePrice());
            item.setServiceUnit(period.getServiceUnit());
            return item;
        }).toList();
    }

    /** 选取指定日期生效的费率方案；历史订单只有单策略字段时退化为该快照。 */
    public RatePlanSnapshot planFor(RateSnapshot snapshot, LocalDate date) {
        if (snapshot.getPlans() != null && !snapshot.getPlans().isEmpty()) {
            for (RatePlanSnapshot plan : snapshot.getPlans()) {
                boolean afterStart = plan.getEffectiveFrom() == null || !date.isBefore(plan.getEffectiveFrom());
                boolean beforeEnd = plan.getEffectiveTo() == null || !date.isAfter(plan.getEffectiveTo());
                if (afterStart && beforeEnd) {
                    return plan;
                }
            }
            throw new ServiceException(500, "订单费率快照缺少 " + date + " 的生效策略");
        }
        RatePlanSnapshot legacy = new RatePlanSnapshot();
        legacy.setStrategyId(snapshot.getStrategyId());
        legacy.setStrategyName(snapshot.getStrategyName());
        legacy.setBillingMode(snapshot.getBillingMode());
        legacy.setPeriods(snapshot.getPeriods());
        return legacy;
    }

    public RateSnapshot parseSnapshot(String json) {
        try {
            return objectMapper.readValue(json, RateSnapshot.class);
        } catch (Exception e) {
            throw new ServiceException(500, "解析订单费率快照失败");
        }
    }

    public void applyMetric(ChargeOrder order, RateSnapshot snapshot, PortMetric metric, long ts) {
        if (metric == null) {
            return;
        }
        Long lastTs = order.getLastMetricTs();
        if (lastTs != null && ts <= lastTs) {
            return;
        }
        BigDecimal meter = to4(metric.getEnergyTotal());
        if (lastTs == null) {
            order.setLastMetricTs(ts);
            order.setLastMeter(meter);
            if (order.getStartMeter() == null && meter != null) {
                order.setStartMeter(meter);
            }
            if (order.getStartTime() == null) {
                order.setStartTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE));
            }
            orderMapper.update(null, freshMetricUpdate(order.getId(), lastTs)
                    .set(ChargeOrder::getLastMetricTs, order.getLastMetricTs())
                    .set(ChargeOrder::getLastMeter, order.getLastMeter())
                    .set(ChargeOrder::getStartMeter, order.getStartMeter())
                    .set(ChargeOrder::getStartTime, order.getStartTime()));
            return;
        }

        int durationSeconds = (int) Math.min(MAX_TICK_SECONDS, Math.max(0, (ts - lastTs) / 1000));
        BigDecimal deltaEnergy = BigDecimal.ZERO.setScale(4);
        if (meter != null) {
            if (order.getLastMeter() != null) {
                BigDecimal diff = meter.subtract(order.getLastMeter());
                if (diff.compareTo(BigDecimal.ZERO) > 0) {
                    deltaEnergy = diff.setScale(4, RoundingMode.HALF_UP);
                } else if (diff.abs().compareTo(METER_ROLLBACK_TOLERANCE) > 0) {
                    log.warn("Order {} meter rollback detected: {} -> {}", order.getOrderNo(),
                            order.getLastMeter(), meter);
                }
            }
            order.setLastMeter(meter);
        } else if (metric.getPowerKw() != null) {
            deltaEnergy = BigDecimal.valueOf(metric.getPowerKw())
                    .multiply(BigDecimal.valueOf(durationSeconds))
                    .divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
        }

        LocalTime localTime = LocalTime.ofInstant(Instant.ofEpochMilli(ts), ZONE);
        LocalDate localDate = LocalDate.ofInstant(Instant.ofEpochMilli(ts), ZONE);
        RatePlanSnapshot plan = planFor(snapshot, localDate);
        RatePeriodSnapshot period = findPeriod(plan.getPeriods(), localTime);
        order.setLastMetricTs(ts);
        order.setLastPeriodType(period.getPeriodType());
        BillingMode mode;
        try {
            mode = BillingMode.parse(plan.getBillingMode());
        } catch (IllegalArgumentException e) {
            throw new ServiceException(500, "订单费率快照计费模式不合法");
        }
        BigDecimal deltaHours = BigDecimal.valueOf(durationSeconds)
                .divide(BigDecimal.valueOf(3600), 8, RoundingMode.HALF_UP);
        BigDecimal timeFee = mode.chargesTime()
                ? money(deltaHours.multiply(price(period.getTimePrice())))
                : money(BigDecimal.ZERO);
        BigDecimal energyFee = mode.chargesEnergy()
                ? money(deltaEnergy.multiply(price(period.getEnergyPrice())))
                : money(BigDecimal.ZERO);
        BigDecimal serviceFee = serviceFee(period, deltaHours, deltaEnergy);

        int newDuration = nz(order.getDurationSeconds()) + durationSeconds;
        BigDecimal newEnergy = money(nz(order.getEnergyKwh()).add(deltaEnergy));
        BigDecimal newTimeFee = money(nz(order.getTimeFee()).add(timeFee));
        BigDecimal newEnergyFee = money(nz(order.getEnergyFee()).add(energyFee));
        BigDecimal newServiceFee = money(nz(order.getServiceFee()).add(serviceFee));
        BigDecimal totalFee = money(newTimeFee.add(newEnergyFee).add(newServiceFee));

        int updated = orderMapper.update(null, freshMetricUpdate(order.getId(), lastTs)
                .set(ChargeOrder::getDurationSeconds, newDuration)
                .set(ChargeOrder::getEnergyKwh, newEnergy)
                .set(ChargeOrder::getTimeFee, newTimeFee)
                .set(ChargeOrder::getEnergyFee, newEnergyFee)
                .set(ChargeOrder::getServiceFee, newServiceFee)
                .set(ChargeOrder::getTotalFee, totalFee)
                .set(ChargeOrder::getLastMetricTs, ts)
                .set(ChargeOrder::getLastMeter, order.getLastMeter())
                .set(ChargeOrder::getLastPeriodType, period.getPeriodType()));
        if (updated == 0) {
            return;
        }
        order.setDurationSeconds(newDuration);
        order.setEnergyKwh(newEnergy);
        order.setTimeFee(newTimeFee);
        order.setEnergyFee(newEnergyFee);
        order.setServiceFee(newServiceFee);
        order.setTotalFee(totalFee);
        accumulateDetail(order.getId(), period, durationSeconds, deltaEnergy, timeFee, energyFee, serviceFee);
    }

    public void applyFinalMeter(ChargeOrder order, RateSnapshot snapshot, BigDecimal finalMeter) {
        if (order.getLastMeter() == null) {
            if (finalMeter != null) {
                order.setStartMeter(finalMeter.setScale(4, RoundingMode.HALF_UP));
                order.setLastMeter(order.getStartMeter());
                order.setEndMeter(order.getStartMeter());
            }
            return;
        }
        BigDecimal meter = finalMeter == null ? null : finalMeter.setScale(4, RoundingMode.HALF_UP);
        if (meter != null && meter.compareTo(order.getLastMeter()) > 0) {
            BigDecimal deltaEnergy = meter.subtract(order.getLastMeter());
            LocalTime at = order.getLastMetricTs() == null
                    ? LocalTime.now(ZONE)
                    : LocalTime.ofInstant(Instant.ofEpochMilli(order.getLastMetricTs()), ZONE);
            LocalDate date = order.getLastMetricTs() == null
                    ? LocalDate.now(ZONE)
                    : LocalDate.ofInstant(Instant.ofEpochMilli(order.getLastMetricTs()), ZONE);
            RatePlanSnapshot plan = planFor(snapshot, date);
            RatePeriodSnapshot period = findPeriod(plan.getPeriods(), at);
            BillingMode mode = BillingMode.parse(plan.getBillingMode());
            BigDecimal energyFee = mode.chargesEnergy()
                    ? money(deltaEnergy.multiply(price(period.getEnergyPrice())))
                    : money(BigDecimal.ZERO);
            BigDecimal serviceFee = serviceFee(period, BigDecimal.ZERO, deltaEnergy);
            BigDecimal newEnergy = money(nz(order.getEnergyKwh()).add(deltaEnergy));
            BigDecimal newEnergyFee = money(nz(order.getEnergyFee()).add(energyFee));
            BigDecimal newServiceFee = money(nz(order.getServiceFee()).add(serviceFee));
            BigDecimal totalFee = money(nz(order.getTimeFee()).add(newEnergyFee).add(newServiceFee));
            orderMapper.update(null, activeOrderUpdate(order.getId())
                    .set(ChargeOrder::getEnergyKwh, newEnergy)
                    .set(ChargeOrder::getEnergyFee, newEnergyFee)
                    .set(ChargeOrder::getServiceFee, newServiceFee)
                    .set(ChargeOrder::getTotalFee, totalFee)
                    .set(ChargeOrder::getLastMeter, meter));
            order.setEnergyKwh(newEnergy);
            order.setEnergyFee(newEnergyFee);
            order.setServiceFee(newServiceFee);
            order.setTotalFee(totalFee);
            order.setLastMeter(meter);
            accumulateDetail(order.getId(), period, 0, deltaEnergy, BigDecimal.ZERO, energyFee, serviceFee);
        }
        if (meter != null) {
            order.setEndMeter(meter);
        } else {
            order.setEndMeter(order.getLastMeter());
        }
    }

    private BigDecimal serviceFee(RatePeriodSnapshot period, BigDecimal deltaHours, BigDecimal deltaEnergy) {
        BigDecimal price = price(period.getServicePrice());
        ServiceUnit unit;
        try {
            unit = ServiceUnit.parse(period.getServiceUnit());
        } catch (IllegalArgumentException e) {
            throw new ServiceException(500, "订单费率快照服务费单位不合法");
        }
        return unit == ServiceUnit.KWH
                ? money(deltaEnergy.multiply(price))
                : money(deltaHours.multiply(price));
    }

    private void accumulateDetail(Long orderId, RatePeriodSnapshot period, int durationSeconds,
                                  BigDecimal deltaEnergy, BigDecimal timeFee,
                                  BigDecimal energyFee, BigDecimal serviceFee) {
        OrderRateDetail detail = detailMapper.selectOne(new LambdaQueryWrapper<OrderRateDetail>()
                .eq(OrderRateDetail::getOrderId, orderId)
                .eq(OrderRateDetail::getPeriodType, period.getPeriodType())
                .last("LIMIT 1"));
        if (detail == null) {
            detail = new OrderRateDetail();
            detail.setOrderId(orderId);
            detail.setPeriodType(period.getPeriodType());
            detail.setTimePrice(price(period.getTimePrice()));
            detail.setEnergyPrice(price(period.getEnergyPrice()));
            detail.setServicePrice(price(period.getServicePrice()));
            detail.setServiceUnit(period.getServiceUnit());
            detail.setDurationSeconds(durationSeconds);
            detail.setEnergyKwh(money(deltaEnergy));
            detail.setTimeFee(money(timeFee));
            detail.setEnergyFee(money(energyFee));
            detail.setServiceFee(money(serviceFee));
            detailMapper.insert(detail);
            return;
        }
        detail.setDurationSeconds(nz(detail.getDurationSeconds()) + durationSeconds);
        detail.setEnergyKwh(money(nz(detail.getEnergyKwh()).add(deltaEnergy)));
        detail.setTimeFee(money(nz(detail.getTimeFee()).add(timeFee)));
        detail.setEnergyFee(money(nz(detail.getEnergyFee()).add(energyFee)));
        detail.setServiceFee(money(nz(detail.getServiceFee()).add(serviceFee)));
        detailMapper.updateById(detail);
    }

    public RatePeriodSnapshot findPeriod(RateSnapshot snapshot, LocalTime time) {
        if (snapshot.getPeriods() == null) {
            throw new ServiceException(500, "订单费率快照没有时段配置");
        }
        return findPeriod(snapshot.getPeriods(), time);
    }

    public RatePeriodSnapshot findPeriod(List<RatePeriodSnapshot> periods, LocalTime time) {
        if (periods == null) {
            throw new ServiceException(500, "订单费率快照没有时段配置");
        }
        for (RatePeriodSnapshot period : periods) {
            LocalTime start = parseTime(period.getStartTime());
            LocalTime end = parseTime(period.getEndTime());
            boolean matched;
            if (start.equals(end)) {
                matched = true;
            } else if (end.isAfter(start)) {
                matched = !time.isBefore(start) && time.isBefore(end);
            } else {
                matched = !time.isBefore(start) || time.isBefore(end);
            }
            if (matched) {
                return period;
            }
        }
        throw new ServiceException(500, "当前时间不在订单费率时段内");
    }

    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal price(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal to4(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(4) : value;
    }

    private LambdaUpdateWrapper<ChargeOrder> activeOrderUpdate(Long orderId) {
        return new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, orderId)
                .in(ChargeOrder::getStatus, OrderStatus.CHARGING.name(), OrderStatus.STOPPING.name());
    }

    /**
     * 测点累计用「本次读到的测点时间戳」做 CAS：共享订阅会把同一设备的相邻报文派给不同实例，
     * 两边都从同一个旧状态起算，后写的绝对值会把先写的增量整笔覆盖掉。
     * 条件不成立说明库里已经落到更新的测点，这一帧直接作废，下一帧从最新状态重新起算，
     * 少算的时间段会被下一帧的增量补回来。
     */
    private LambdaUpdateWrapper<ChargeOrder> freshMetricUpdate(Long orderId, Long readLastTs) {
        LambdaUpdateWrapper<ChargeOrder> wrapper = activeOrderUpdate(orderId);
        return readLastTs == null
                ? wrapper.isNull(ChargeOrder::getLastMetricTs)
                : wrapper.eq(ChargeOrder::getLastMetricTs, readLastTs);
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, HH_MM);
        } catch (DateTimeParseException e) {
            throw new ServiceException(500, "订单费率快照时间格式不合法");
        }
    }
}
