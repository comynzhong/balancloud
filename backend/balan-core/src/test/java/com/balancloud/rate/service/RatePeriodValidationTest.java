package com.balancloud.rate.service;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.rate.domain.RatePeriod;
import com.balancloud.rate.dto.RatePeriodRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatePeriodValidationTest {

    private final RateService rateService = new RateService(null, null, null, null);

    @Test
    void acceptsPeakFlatValleyCoveringWholeDayAcrossMidnight() {
        List<RatePeriod> periods = rateService.validateAndBuild(List.of(
                request("VALLEY", "22:00", "06:00", "0.5000", "0.3000", "0.2000", "KWH"),
                request("FLAT", "06:00", "08:00", "0.7000", "0.6000", "0.3000", "KWH"),
                request("PEAK", "08:00", "22:00", "1.0000", "0.9000", "0.4000", "KWH")));

        assertThat(periods).hasSize(3);
        assertThat(periods).extracting(RatePeriod::getPeriodType)
                .containsExactly("VALLEY", "FLAT", "PEAK");
        assertThat(periods.get(0).getStartTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(periods.get(0).getEndTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(periods.get(2).getTimePrice()).isEqualByComparingTo("1.0000");
        assertThat(periods.get(2).getTimePrice().scale()).isEqualTo(4);
    }

    @Test
    void acceptsSingleWholeDayWindow() {
        List<RatePeriod> periods = rateService.validateAndBuild(List.of(
                request("FLAT", "00:00", "00:00", "0.7000", "0.6000", "0.3000", "HOUR")));

        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).getServiceUnit()).isEqualTo("HOUR");
    }

    @Test
    void rejectsGapInCoverage() {
        assertThatThrownBy(() -> rateService.validateAndBuild(List.of(
                request("PEAK", "08:00", "12:00"),
                request("VALLEY", "14:00", "08:00"))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("费率时段未覆盖全天，请补齐空档时段");
    }

    @Test
    void rejectsOverlappingWindows() {
        assertThatThrownBy(() -> rateService.validateAndBuild(List.of(
                request("PEAK", "08:00", "22:00"),
                request("FLAT", "06:00", "09:00"),
                request("VALLEY", "22:00", "06:00"))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("费率时段存在重叠，请检查峰平谷时间");
    }

    @Test
    void rejectsSameTypeWithDifferentPrices() {
        assertThatThrownBy(() -> rateService.validateAndBuild(List.of(
                request("PEAK", "08:00", "12:00", "1.0000", "0.9000", "0.4000", "KWH"),
                request("PEAK", "14:00", "18:00", "1.2000", "0.9000", "0.4000", "KWH"),
                request("FLAT", "12:00", "14:00"),
                request("VALLEY", "18:00", "08:00"))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("同一时段类型的价格和服务费单位必须一致");
    }

    @Test
    void rejectsSameTypeWithDifferentServiceUnit() {
        assertThatThrownBy(() -> rateService.validateAndBuild(List.of(
                request("PEAK", "08:00", "12:00", "1.0000", "0.9000", "0.4000", "KWH"),
                request("PEAK", "14:00", "18:00", "1.0000", "0.9000", "0.4000", "HOUR"),
                request("FLAT", "12:00", "14:00"),
                request("VALLEY", "18:00", "08:00"))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("同一时段类型的价格和服务费单位必须一致");
    }

    @Test
    void rejectsEmptyPeriodList() {
        assertThatThrownBy(() -> rateService.validateAndBuild(List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("至少配置一个费率时段");
    }

    @Test
    void rejectsUnknownPeriodType() {
        assertThatThrownBy(() -> rateService.validateAndBuild(List.of(request("SHARP", "00:00", "00:00"))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("时段类型不合法");
    }

    @Test
    void rejectsMalformedTime() {
        assertThatThrownBy(() -> rateService.validateAndBuild(List.of(request("PEAK", "8am", "22:00"))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("时间格式应为 HH:mm");
    }

    private static RatePeriodRequest request(String periodType, String startTime, String endTime) {
        return request(periodType, startTime, endTime, "1.0000", "0.9000", "0.4000", "KWH");
    }

    private static RatePeriodRequest request(String periodType, String startTime, String endTime,
                                             String timePrice, String energyPrice,
                                             String servicePrice, String serviceUnit) {
        RatePeriodRequest request = new RatePeriodRequest();
        request.setPeriodType(periodType);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setTimePrice(new BigDecimal(timePrice));
        request.setEnergyPrice(new BigDecimal(energyPrice));
        request.setServicePrice(new BigDecimal(servicePrice));
        request.setServiceUnit(serviceUnit);
        return request;
    }
}
