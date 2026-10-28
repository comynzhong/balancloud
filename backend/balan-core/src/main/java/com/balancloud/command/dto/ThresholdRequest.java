package com.balancloud.command.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ThresholdRequest {

    @NotNull(message = "过温阈值不能为空")
    private BigDecimal overTempC;

    @NotNull(message = "过流阈值不能为空")
    private BigDecimal overCurrentA;
}
