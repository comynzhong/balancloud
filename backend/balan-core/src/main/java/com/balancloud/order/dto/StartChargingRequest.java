package com.balancloud.order.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StartChargingRequest {

    @NotNull(message = "请设置最大充电时长")
    @Min(value = 1, message = "充电时长至少 1 分钟")
    @Max(value = 1440, message = "充电时长不能超过 1440 分钟")
    private Integer maxDurationMinutes;

    @NotNull(message = "请设置金额上限")
    @DecimalMin(value = "0.01", message = "金额上限必须大于 0")
    @DecimalMax(value = "100000", message = "金额上限不能超过 100000 元")
    @Digits(integer = 8, fraction = 4, message = "金额上限最多 4 位小数")
    private BigDecimal chargeLimitAmount;

    private Long vehicleId;
}
