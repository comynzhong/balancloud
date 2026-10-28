package com.balancloud.order.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.domain.OrderRateDetail;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.mapper.OrderRateDetailMapper;
import com.balancloud.order.model.RatePeriodSnapshot;
import com.balancloud.order.model.RateSnapshot;
import com.balancloud.support.TableInfoSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingServiceTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private ChargeOrderMapper orderMapper;
    private OrderRateDetailMapper detailMapper;
    private BillingService billingService;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoSupport.init(ChargeOrder.class, OrderRateDetail.class);
    }

    @BeforeEach
    void setUp() {
        orderMapper = mock(ChargeOrderMapper.class);
        detailMapper = mock(OrderRateDetailMapper.class);
        billingService = new BillingService(orderMapper, detailMapper, new ObjectMapper());
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        when(detailMapper.selectOne(any())).thenReturn(null);
    }

    @Test
    void mixedModeChargesTimeEnergyAndServicePerKwh() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0000");

        billingService.applyMetric(order, snapshot("MIXED",
                        period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")),
                metric(2.2, 40.0, 100.02), at("2026-10-23T10:01:00"));

        assertThat(order.getDurationSeconds()).isEqualTo(60);
        assertThat(order.getEnergyKwh()).isEqualByComparingTo("0.0200");
        assertThat(order.getTimeFee()).isEqualByComparingTo("0.0167");
        assertThat(order.getEnergyFee()).isEqualByComparingTo("0.0180");
        assertThat(order.getServiceFee()).isEqualByComparingTo("0.0080");
        assertThat(order.getTotalFee()).isEqualByComparingTo("0.0427");
        assertThat(order.getLastPeriodType()).isEqualTo("PEAK");

        OrderRateDetail detail = insertedDetail();
        assertThat(detail.getPeriodType()).isEqualTo("PEAK");
        assertThat(detail.getDurationSeconds()).isEqualTo(60);
        assertThat(detail.getEnergyKwh()).isEqualByComparingTo("0.0200");
        assertThat(detail.getTimeFee()).isEqualByComparingTo("0.0167");
    }

    @Test
    void timeModeIgnoresEnergyAndChargesServicePerHour() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0000");

        billingService.applyMetric(order, snapshot("TIME",
                        period("FLAT", "00:00", "00:00", "1.2000", "0.9000", "0.5000", "HOUR")),
                metric(2.2, 40.0, 100.02), at("2026-10-23T10:01:00"));

        assertThat(order.getTimeFee()).isEqualByComparingTo("0.0200");
        assertThat(order.getEnergyFee()).isEqualByComparingTo("0.0000");
        assertThat(order.getServiceFee()).isEqualByComparingTo("0.0083");
        assertThat(order.getTotalFee()).isEqualByComparingTo("0.0283");
        assertThat(order.getEnergyKwh()).isEqualByComparingTo("0.0200");
    }

    @Test
    void energyModeIgnoresTimeFee() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0000");

        billingService.applyMetric(order, snapshot("ENERGY",
                        period("PEAK", "08:00", "22:00", "1.0000", "0.3000", "0.2000", "KWH")),
                metric(2.2, 40.0, 100.50), at("2026-10-23T10:01:00"));

        assertThat(order.getTimeFee()).isEqualByComparingTo("0.0000");
        assertThat(order.getEnergyFee()).isEqualByComparingTo("0.1500");
        assertThat(order.getServiceFee()).isEqualByComparingTo("0.1000");
        assertThat(order.getTotalFee()).isEqualByComparingTo("0.2500");
    }

    @Test
    void clampsLongGapsToFiveMinutes() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0000");

        billingService.applyMetric(order, snapshot("MIXED",
                        period("PEAK", "08:00", "22:00", "3.6000", "0.0000", "0.0000", "KWH")),
                metric(2.2, 40.0, 100.0), at("2026-10-23T11:00:00"));

        assertThat(order.getDurationSeconds()).isEqualTo(300);
        assertThat(order.getTimeFee()).isEqualByComparingTo("0.3000");
    }

    @Test
    void ignoresMeterRollbackWithinTolerance() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0500");

        billingService.applyMetric(order, snapshot("MIXED",
                        period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")),
                metric(2.2, 40.0, 100.00), at("2026-10-23T10:01:00"));

        assertThat(order.getEnergyKwh()).isEqualByComparingTo("0.0000");
        assertThat(order.getEnergyFee()).isEqualByComparingTo("0.0000");
        assertThat(order.getTimeFee()).isEqualByComparingTo("0.0167");
        assertThat(order.getLastMeter()).isEqualByComparingTo("100.0000");
    }

    @Test
    void ignoresMeterRollbackBeyondToleranceButKeepsBilling() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "150.0000");

        billingService.applyMetric(order, snapshot("MIXED",
                        period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")),
                metric(2.2, 40.0, 100.00), at("2026-10-23T10:01:00"));

        assertThat(order.getEnergyKwh()).isEqualByComparingTo("0.0000");
        assertThat(order.getLastMeter()).isEqualByComparingTo("100.0000");
    }

    @Test
    void ignoresStaleOrDuplicateMetric() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0000");

        billingService.applyMetric(order, snapshot("MIXED",
                        period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")),
                metric(2.2, 40.0, 100.90), at("2026-10-23T10:00:00"));

        assertThat(order.getDurationSeconds()).isZero();
        assertThat(order.getTotalFee()).isEqualByComparingTo("0.0000");
        verify(orderMapper, never()).update(isNull(), any());
        verify(detailMapper, never()).insert(any(OrderRateDetail.class));
    }

    /** 另一实例已写入更新测点时，本节点的旧帧整笔作废：条件里带上测点时间戳。 */
    @Test
    void staleFrameCannotOverwriteNewerStateWrittenByAnotherInstance() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0000");
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        billingService.applyMetric(order, snapshot("MIXED",
                        period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")),
                metric(2.2, 40.0, 100.02), at("2026-10-23T10:01:00"));

        assertThat(order.getEnergyKwh()).isEqualByComparingTo("0.0000");
        assertThat(order.getTotalFee()).isEqualByComparingTo("0.0000");
        verify(detailMapper, never()).insert(any(OrderRateDetail.class));

        ArgumentCaptor<LambdaUpdateWrapper<ChargeOrder>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("last_metric_ts");
    }

    /** 基线帧（库里还没有测点）只能在测点仍为空时写入，避免两个实例各写一份不同的起始电表。 */
    @Test
    void baselineMetricWriteRequiresEmptyStoredTimestamp() {
        ChargeOrder order = new ChargeOrder();
        order.setId(1L);
        order.setOrderNo("CO-TEST");
        order.setStatus("CHARGING");

        billingService.applyMetric(order, snapshot("MIXED",
                        period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")),
                metric(2.2, 40.0, 100.0), at("2026-10-23T10:00:00"));

        ArgumentCaptor<LambdaUpdateWrapper<ChargeOrder>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("last_metric_ts IS NULL");
    }

    @Test
    void firstMetricOnlyInitialisesBaselineWithoutFees() {
        ChargeOrder order = new ChargeOrder();
        order.setId(1L);
        order.setOrderNo("CO-TEST");
        order.setStatus("CHARGING");

        billingService.applyMetric(order, snapshot("MIXED",
                        period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")),
                metric(2.2, 40.0, 100.0), at("2026-10-23T10:00:00"));

        assertThat(order.getStartMeter()).isEqualByComparingTo("100.0000");
        assertThat(order.getStartTime()).isEqualTo(LocalDateTime.of(2026, 10, 23, 10, 0));
        assertThat(order.getDurationSeconds()).isNull();
        assertThat(order.getTotalFee()).isNull();
        verify(detailMapper, never()).insert(any(OrderRateDetail.class));
    }

    @Test
    void splitsFeesAcrossPeriodChange() {
        ChargeOrder order = chargedOrder("2026-10-23T21:58:59", "100.0000");
        RateSnapshot snapshot = snapshot("MIXED",
                period("PEAK", "08:00", "22:00", "2.0000", "1.0000", "0.0000", "KWH"),
                period("VALLEY", "22:00", "08:00", "0.6000", "0.3000", "0.0000", "KWH"));

        billingService.applyMetric(order, snapshot, metric(2.2, 40.0, 100.01), at("2026-10-23T21:59:59"));
        billingService.applyMetric(order, snapshot, metric(2.2, 40.0, 100.02), at("2026-10-23T22:00:59"));

        assertThat(order.getLastPeriodType()).isEqualTo("VALLEY");
        assertThat(order.getTimeFee()).isEqualByComparingTo("0.0433");

        ArgumentCaptor<OrderRateDetail> captor = ArgumentCaptor.forClass(OrderRateDetail.class);
        verify(detailMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(OrderRateDetail::getPeriodType)
                .containsExactly("PEAK", "VALLEY");
        assertThat(captor.getAllValues().get(0).getTimeFee()).isEqualByComparingTo("0.0333");
        assertThat(captor.getAllValues().get(1).getTimeFee()).isEqualByComparingTo("0.0100");
    }

    @Test
    void selectsPlanByMetricDateAcrossMidnight() {
        ChargeOrder order = chargedOrder("2026-10-23T23:59:00", "100.0000");
        RateSnapshot snapshot = snapshot("MIXED",
                period("ALL", "00:00", "00:00", "0.0000", "0.0000", "0.0000", "KWH"));
        snapshot.setPlans(java.util.List.of(
                plan(null, java.time.LocalDate.of(2026, 10, 23), null, "0.0000", "1.0000"),
                plan(java.time.LocalDate.of(2026, 10, 24), null, null, "0.0000", "2.0000")));

        billingService.applyMetric(order, snapshot, metric(2.2, 40.0, 100.01), at("2026-10-23T23:59:59"));
        billingService.applyMetric(order, snapshot, metric(2.2, 40.0, 100.03), at("2026-10-24T00:00:59"));

        assertThat(order.getEnergyKwh()).isEqualByComparingTo("0.0300");
        assertThat(order.getEnergyFee()).isEqualByComparingTo("0.0500");
        assertThat(order.getTotalFee()).isEqualByComparingTo("0.0500");
    }

    @Test
    void rejectsMetricDateWithoutMatchingPlan() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0000");
        RateSnapshot snapshot = snapshot("MIXED",
                period("ALL", "00:00", "00:00", "0.0000", "1.0000", "0.0000", "KWH"));
        snapshot.setPlans(java.util.List.of(
                plan(java.time.LocalDate.of(2026, 10, 24), null, null, "0.0000", "1.0000")));

        assertThatThrownBy(() -> billingService.applyMetric(order, snapshot,
                metric(2.2, 40.0, 100.02), at("2026-10-23T10:01:00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("2026-10-23");
    }

    @Test
    void accumulatesIntoExistingPeriodDetail() {
        OrderRateDetail existing = new OrderRateDetail();
        existing.setOrderId(1L);
        existing.setPeriodType("PEAK");
        existing.setDurationSeconds(60);
        existing.setEnergyKwh(new BigDecimal("0.0200"));
        existing.setTimeFee(new BigDecimal("0.0167"));
        existing.setEnergyFee(new BigDecimal("0.0180"));
        existing.setServiceFee(new BigDecimal("0.0080"));
        when(detailMapper.selectOne(any())).thenReturn(existing);

        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0000");
        billingService.applyMetric(order, snapshot("MIXED",
                        period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")),
                metric(2.2, 40.0, 100.02), at("2026-10-23T10:01:00"));

        ArgumentCaptor<OrderRateDetail> captor = ArgumentCaptor.forClass(OrderRateDetail.class);
        verify(detailMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDurationSeconds()).isEqualTo(120);
        assertThat(captor.getValue().getEnergyKwh()).isEqualByComparingTo("0.0400");
        assertThat(captor.getValue().getTimeFee()).isEqualByComparingTo("0.0334");
        verify(detailMapper, never()).insert(any(OrderRateDetail.class));
    }

    @Test
    void settlesRemainingEnergyFromFinalMeter() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0000");
        order.setEnergyKwh(new BigDecimal("0.0200"));
        order.setEnergyFee(new BigDecimal("0.0180"));
        order.setServiceFee(new BigDecimal("0.0080"));
        order.setTotalFee(new BigDecimal("0.0260"));

        billingService.applyFinalMeter(order, snapshot("MIXED",
                period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")), new BigDecimal("100.1000"));

        assertThat(order.getEnergyKwh()).isEqualByComparingTo("0.1200");
        assertThat(order.getEnergyFee()).isEqualByComparingTo("0.1080");
        assertThat(order.getServiceFee()).isEqualByComparingTo("0.0480");
        assertThat(order.getEndMeter()).isEqualByComparingTo("100.1000");
    }

    @Test
    void finalMeterReadingBelowBaselineKeepsBilledEnergyButIsRecorded() {
        ChargeOrder order = chargedOrder("2026-10-23T10:00:00", "100.0000");
        order.setEnergyKwh(new BigDecimal("0.0200"));

        billingService.applyFinalMeter(order, snapshot("MIXED",
                period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")), new BigDecimal("99.9000"));

        assertThat(order.getEnergyKwh()).isEqualByComparingTo("0.0200");
        assertThat(order.getEndMeter()).isEqualByComparingTo("99.9000");
        verify(detailMapper, never()).insert(any(OrderRateDetail.class));
    }

    private OrderRateDetail insertedDetail() {
        ArgumentCaptor<OrderRateDetail> captor = ArgumentCaptor.forClass(OrderRateDetail.class);
        verify(detailMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static ChargeOrder chargedOrder(String lastMetricAt, String lastMeter) {
        ChargeOrder order = new ChargeOrder();
        order.setId(1L);
        order.setOrderNo("CO-TEST");
        order.setStatus("CHARGING");
        order.setDurationSeconds(0);
        order.setEnergyKwh(new BigDecimal("0.0000"));
        order.setTimeFee(new BigDecimal("0.0000"));
        order.setEnergyFee(new BigDecimal("0.0000"));
        order.setServiceFee(new BigDecimal("0.0000"));
        order.setTotalFee(new BigDecimal("0.0000"));
        order.setLastMetricTs(at(lastMetricAt));
        order.setLastMeter(new BigDecimal(lastMeter));
        return order;
    }

    private static PortMetric metric(Double powerKw, Double temperature, Double energyTotal) {
        PortMetric metric = new PortMetric();
        metric.setPortNo(1);
        metric.setStatus("CHARGING");
        metric.setVoltage(220.0);
        metric.setCurrentA(10.0);
        metric.setPowerKw(powerKw);
        metric.setTemperature(temperature);
        metric.setEnergyTotal(energyTotal);
        return metric;
    }

    private static com.balancloud.order.model.RatePlanSnapshot plan(java.time.LocalDate from,
                                                                    java.time.LocalDate to,
                                                                    Long serviceStrategyId,
                                                                    String timePrice,
                                                                    String energyPrice) {
        com.balancloud.order.model.RatePlanSnapshot plan = new com.balancloud.order.model.RatePlanSnapshot();
        plan.setStrategyId(1L);
        plan.setStrategyName("单元测试策略");
        plan.setServiceStrategyId(serviceStrategyId);
        plan.setBillingMode("MIXED");
        plan.setEffectiveFrom(from);
        plan.setEffectiveTo(to);
        plan.setPeriods(java.util.List.of(
                period("ALL", "00:00", "00:00", timePrice, energyPrice, "0.0000", "KWH")));
        return plan;
    }

    private static RateSnapshot snapshot(String billingMode, RatePeriodSnapshot... periods) {
        RateSnapshot snapshot = new RateSnapshot();
        snapshot.setStrategyId(1L);
        snapshot.setStrategyName("单元测试策略");
        snapshot.setBillingMode(billingMode);
        snapshot.setPeriods(java.util.List.of(periods));
        return snapshot;
    }

    private static RatePeriodSnapshot period(String periodType, String startTime, String endTime,
                                             String timePrice, String energyPrice,
                                             String servicePrice, String serviceUnit) {
        RatePeriodSnapshot period = new RatePeriodSnapshot();
        period.setPeriodType(periodType);
        period.setStartTime(startTime);
        period.setEndTime(endTime);
        period.setTimePrice(new BigDecimal(timePrice));
        period.setEnergyPrice(new BigDecimal(energyPrice));
        period.setServicePrice(new BigDecimal(servicePrice));
        period.setServiceUnit(serviceUnit);
        return period;
    }

    private static long at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SHANGHAI).toInstant().toEpochMilli();
    }
}
