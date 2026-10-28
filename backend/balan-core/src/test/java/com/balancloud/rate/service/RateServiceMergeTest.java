package com.balancloud.rate.service;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.rate.domain.RatePeriod;
import com.balancloud.rate.dto.RatePeriodRequest;
import com.balancloud.rate.model.StrategyType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateServiceMergeTest {

    private final RateService rateService = new RateService(null, null, null, null);

    @Test
    void mergesServicePeriodsIntoEnergySegments() {
        List<RatePeriod> energy = List.of(
                period("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.0000"),
                period("VALLEY", "22:00", "08:00", "0.6000", "0.3000", "0.0000"));
        List<RatePeriod> service = List.of(
                period("SERVICE", "00:00", "12:00", "0.0000", "0.0000", "0.1000"),
                period("SERVICE", "12:00", "00:00", "0.0000", "0.0000", "0.2000"));

        List<RatePeriod> merged = rateService.mergeServicePeriods(energy, service);

        assertThat(merged).hasSize(4);
        assertThat(merged).extracting(RatePeriod::getStartTime)
                .containsExactly(LocalTime.MIDNIGHT, LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(22, 0));
        assertThat(merged).extracting(RatePeriod::getEndTime)
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(22, 0), LocalTime.MIDNIGHT);
        assertThat(merged).extracting(RatePeriod::getPeriodType)
                .containsExactly("VALLEY", "PEAK", "PEAK", "VALLEY");
        assertThat(merged).extracting(RatePeriod::getServicePrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.1000"), new BigDecimal("0.1000"),
                        new BigDecimal("0.2000"), new BigDecimal("0.2000"));
        assertThat(merged.get(2).getEnergyPrice()).isEqualByComparingTo("0.9000");
        assertThat(merged.get(3).getEnergyPrice()).isEqualByComparingTo("0.3000");
    }

    @Test
    void rejectsServicePeriodsWithGaps() {
        List<RatePeriod> energy = List.of(period("ALL", "00:00", "00:00", "1.0000", "0.9000", "0.0000"));
        List<RatePeriod> service = List.of(period("SERVICE", "00:00", "12:00", "0.0000", "0.0000", "0.1000"));

        assertThatThrownBy(() -> rateService.mergeServicePeriods(energy, service))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未覆盖全天");
    }

    @Test
    void serviceStrategyIgnoresTimeAndEnergyPrices() {
        RatePeriodRequest request = new RatePeriodRequest();
        request.setPeriodType("FLAT");
        request.setStartTime("00:00");
        request.setEndTime("00:00");
        request.setTimePrice(new BigDecimal("1.0000"));
        request.setEnergyPrice(new BigDecimal("2.0000"));
        request.setServicePrice(new BigDecimal("0.5000"));
        request.setServiceUnit("KWH");

        List<RatePeriod> periods = rateService.validateAndBuild(List.of(request), StrategyType.SERVICE);

        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).getTimePrice()).isEqualByComparingTo("0.0000");
        assertThat(periods.get(0).getEnergyPrice()).isEqualByComparingTo("0.0000");
        assertThat(periods.get(0).getServicePrice()).isEqualByComparingTo("0.5000");
    }

    @Test
    void acceptsPeriodEndingAtMidnight() {
        RatePeriodRequest day = new RatePeriodRequest();
        day.setPeriodType("FLAT");
        day.setStartTime("00:00");
        day.setEndTime("12:00");
        day.setTimePrice(BigDecimal.ONE);
        day.setEnergyPrice(BigDecimal.ONE);
        day.setServicePrice(BigDecimal.ZERO);
        day.setServiceUnit("KWH");
        RatePeriodRequest night = new RatePeriodRequest();
        night.setPeriodType("PEAK");
        night.setStartTime("12:00");
        night.setEndTime("00:00");
        night.setTimePrice(BigDecimal.ONE);
        night.setEnergyPrice(BigDecimal.ONE);
        night.setServicePrice(BigDecimal.ZERO);
        night.setServiceUnit("KWH");

        List<RatePeriod> periods = rateService.validateAndBuild(List.of(day, night));

        assertThat(periods).hasSize(2);
        assertThat(periods.get(1).getEndTime()).isEqualTo(LocalTime.MIDNIGHT);
    }

    private static RatePeriod period(String type, String start, String end,
                                     String timePrice, String energyPrice, String servicePrice) {
        RatePeriod period = new RatePeriod();
        period.setPeriodType(type);
        period.setStartTime(LocalTime.parse(start));
        period.setEndTime(LocalTime.parse(end));
        period.setTimePrice(new BigDecimal(timePrice));
        period.setEnergyPrice(new BigDecimal(energyPrice));
        period.setServicePrice(new BigDecimal(servicePrice));
        period.setServiceUnit("KWH");
        return period;
    }
}
