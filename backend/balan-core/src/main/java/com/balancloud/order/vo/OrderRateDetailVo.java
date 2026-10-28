package com.balancloud.order.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderRateDetailVo {

    private Long id;
    private String periodType;
    private BigDecimal timePrice;
    private BigDecimal energyPrice;
    private BigDecimal servicePrice;
    private String serviceUnit;
    private Integer durationSeconds;
    private BigDecimal energyKwh;
    private BigDecimal timeFee;
    private BigDecimal energyFee;
    private BigDecimal serviceFee;
}
