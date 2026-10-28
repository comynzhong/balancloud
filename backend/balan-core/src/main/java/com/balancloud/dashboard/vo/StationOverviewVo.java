package com.balancloud.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 首页「场站明细」行：单个场站的资产、实时负荷与今日经营。 */
@Data
public class StationOverviewVo {

    private Long stationId;
    private String stationName;
    private String status;
    private BigDecimal capacityKw;

    private long deviceCount;
    private long onlineDeviceCount;
    private long portCount;
    private long chargingPortCount;
    private long faultPortCount;

    private BigDecimal currentPowerKw;
    private Long powerSampleAt;

    private long todayOrderCount;
    private BigDecimal todayEnergyKwh;
    private BigDecimal todayRevenue;
    private long openAlertCount;
}
