package com.balancloud.app.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AppRatePeriodVo {

    private String periodType;
    private String periodLabel;
    private String startTime;
    private String endTime;
    private BigDecimal timePrice;
    private BigDecimal energyPrice;
    private BigDecimal servicePrice;
    private String serviceUnit;
}
