package com.balancloud.order.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RatePeriodSnapshot {

    private String periodType;

    private String startTime;

    private String endTime;

    private BigDecimal timePrice;

    private BigDecimal energyPrice;

    private BigDecimal servicePrice;

    private String serviceUnit;
}
