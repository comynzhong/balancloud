package com.balancloud.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DashboardSummaryVo {

    private long stationCount;
    private long deviceCount;
    private long onlineDeviceCount;
    private long portCount;
    private long chargingPortCount;
    private long idlePortCount;
    private long faultPortCount;

    private BigDecimal currentPowerKw;
    private Long powerSampleAt;

    private long todayOrderCount;
    private BigDecimal todayEnergyKwh;
    private BigDecimal todayRevenue;
    private BigDecimal todayEnergyFee;
    private BigDecimal todayServiceFee;
    private long todayUnpaidCount;
    private long todayRefundCount;
    private BigDecimal todayRefundAmount;

    private long yesterdayOrderCount;
    private BigDecimal yesterdayEnergyKwh;
    private BigDecimal yesterdayRevenue;

    private long monthOrderCount;
    private BigDecimal monthEnergyKwh;
    private BigDecimal monthRevenue;
    private BigDecimal monthEnergyFee;
    private BigDecimal monthServiceFee;
    private BigDecimal lastMonthRevenue;

    private long yearOrderCount;
    private BigDecimal yearEnergyKwh;
    private BigDecimal yearRevenue;
    private BigDecimal yearEnergyFee;
    private BigDecimal yearServiceFee;
    private BigDecimal lastYearRevenue;

    private long activeOrderCount;

    private long openAlertCount;
    private long criticalAlertCount;
    private long todayAlertCount;

    private int wsClients;
}
