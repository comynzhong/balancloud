package com.balancloud.report.service;

import com.balancloud.report.vo.MorningReportVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MorningReportServiceTest {

    /** 站内信渠道会把正文截到 500 字，晨报正文必须自己留足余量，否则末尾的场站行会被切掉。 */
    @Test
    void messageContentFitsTheInAppLimit() {
        MorningReportVo report = sample();
        report.setTopStations(List.of(
                station("城南综合能源站（一期扩建）", "12345.60", "98765.43"),
                station("高新产业园地下车库充电桩群", "5432.10", "43210.98"),
                station("火车站东广场快充站", "999.99", "1234.56")));

        String content = MorningReportService.content(report);

        assertThat(content).hasSizeLessThanOrEqualTo(500);
        assertThat(content).contains("火车站东广场快充站");
    }

    @Test
    void titleCarriesDateRevenueAndOrderCount() {
        MorningReportVo report = sample();

        assertThat(MorningReportService.title(report))
                .isEqualTo("运营晨报 · 10月25日：营收 ¥1234.50，订单 20 笔");
    }

    @Test
    void growthNeedsANonZeroBase() {
        assertThat(MorningReportService.growthPercent(BigDecimal.valueOf(150), BigDecimal.valueOf(100)))
                .isEqualByComparingTo("50.0");
        assertThat(MorningReportService.growthPercent(BigDecimal.TEN, BigDecimal.ZERO)).isNull();
        assertThat(MorningReportService.growthPercent(BigDecimal.TEN, null)).isNull();
        assertThat(MorningReportService.growthPercent(BigDecimal.valueOf(50), BigDecimal.valueOf(100)))
                .isEqualByComparingTo("-50.0");
    }

    private MorningReportVo sample() {
        MorningReportVo report = new MorningReportVo();
        report.setDate(LocalDate.of(2026, 10, 25));
        report.setOrderCount(20);
        report.setCompletedCount(18);
        report.setAbnormalCount(1);
        report.setFailedCount(1);
        report.setEnergyKwh(new BigDecimal("2345.67"));
        report.setRevenue(new BigDecimal("1234.50"));
        report.setEnergyFee(new BigDecimal("800.00"));
        report.setServiceFee(new BigDecimal("434.50"));
        report.setRefundCount(1);
        report.setRefundAmount(new BigDecimal("50.00"));
        report.setNewUserCount(7);
        report.setActiveUserCount(12);
        report.setAlertCount(3);
        report.setCriticalAlertCount(1);
        report.setMajorAlertCount(1);
        report.setUnresolvedAlertCount(2);
        report.setDeviceCount(40);
        report.setOnlineDeviceCount(38);
        report.setFaultPortCount(2);
        return report;
    }

    private MorningReportVo.StationLine station(String name, String energy, String revenue) {
        MorningReportVo.StationLine line = new MorningReportVo.StationLine();
        line.setStationName(name);
        line.setEnergyKwh(new BigDecimal(energy));
        line.setRevenue(new BigDecimal(revenue));
        return line;
    }
}
