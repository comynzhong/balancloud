package com.balancloud.command.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CommandRequest {

    private String type;

    private Integer maxDurationMinutes;

    private BigDecimal chargeLimitAmount;

    private BigDecimal overTempC;

    private BigDecimal overCurrentA;
}
