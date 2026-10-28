package com.balancloud.rate.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RatePeriodVo {

    private Long id;
    private String periodType;
    private String startTime;
    private String endTime;
    private BigDecimal timePrice;
    private BigDecimal energyPrice;
    private BigDecimal servicePrice;
    private String serviceUnit;
}
