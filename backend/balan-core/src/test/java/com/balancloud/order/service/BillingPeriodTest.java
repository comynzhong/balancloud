package com.balancloud.order.service;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.order.model.RatePeriodSnapshot;
import com.balancloud.order.model.RateSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingPeriodTest {

    private final BillingService billingService = new BillingService(null, null, null);

    @Test
    void matchesNormalWindow() {
        RateSnapshot snapshot = snapshot(period("PEAK", "08:00", "22:00"));

        assertThat(billingService.findPeriod(snapshot, LocalTime.of(8, 0)).getPeriodType()).isEqualTo("PEAK");
        assertThat(billingService.findPeriod(snapshot, LocalTime.of(21, 59)).getPeriodType()).isEqualTo("PEAK");
    }

    @Test
    void excludesWindowEnd() {
        RateSnapshot snapshot = snapshot(period("PEAK", "08:00", "22:00"), period("VALLEY", "22:00", "08:00"));

        assertThat(billingService.findPeriod(snapshot, LocalTime.of(22, 0)).getPeriodType()).isEqualTo("VALLEY");
    }

    @Test
    void matchesAcrossMidnight() {
        RateSnapshot snapshot = snapshot(period("VALLEY", "22:00", "06:00"), period("PEAK", "06:00", "22:00"));

        assertThat(billingService.findPeriod(snapshot, LocalTime.of(23, 30)).getPeriodType()).isEqualTo("VALLEY");
        assertThat(billingService.findPeriod(snapshot, LocalTime.of(2, 0)).getPeriodType()).isEqualTo("VALLEY");
        assertThat(billingService.findPeriod(snapshot, LocalTime.of(6, 0)).getPeriodType()).isEqualTo("PEAK");
    }

    @Test
    void treatsEqualStartAndEndAsWholeDay() {
        RateSnapshot snapshot = snapshot(period("FLAT", "00:00", "00:00"));

        assertThat(billingService.findPeriod(snapshot, LocalTime.of(0, 0)).getPeriodType()).isEqualTo("FLAT");
        assertThat(billingService.findPeriod(snapshot, LocalTime.of(13, 37)).getPeriodType()).isEqualTo("FLAT");
        assertThat(billingService.findPeriod(snapshot, LocalTime.of(23, 59)).getPeriodType()).isEqualTo("FLAT");
    }

    @Test
    void rejectsTimeOutsideConfiguredPeriods() {
        RateSnapshot snapshot = snapshot(period("PEAK", "08:00", "12:00"));

        assertThatThrownBy(() -> billingService.findPeriod(snapshot, LocalTime.of(18, 0)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前时间不在订单费率时段内");
    }

    @Test
    void rejectsBlankSnapshot() {
        RateSnapshot snapshot = new RateSnapshot();

        assertThatThrownBy(() -> billingService.findPeriod(snapshot, LocalTime.of(9, 0)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("订单费率快照没有时段配置");
    }

    private static RateSnapshot snapshot(RatePeriodSnapshot... periods) {
        RateSnapshot snapshot = new RateSnapshot();
        snapshot.setBillingMode("MIXED");
        snapshot.setPeriods(List.of(periods));
        return snapshot;
    }

    private static RatePeriodSnapshot period(String type, String start, String end) {
        RatePeriodSnapshot period = new RatePeriodSnapshot();
        period.setPeriodType(type);
        period.setStartTime(start);
        period.setEndTime(end);
        period.setTimePrice(new BigDecimal("1.0000"));
        period.setEnergyPrice(new BigDecimal("0.9000"));
        period.setServicePrice(new BigDecimal("0.4000"));
        period.setServiceUnit("KWH");
        return period;
    }
}
