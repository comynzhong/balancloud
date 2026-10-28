package com.balancloud.device.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SimConfigRequest {

    @NotNull(message = "上报间隔不能为空")
    @Min(value = 1, message = "上报间隔不能小于 1 秒")
    @Max(value = 60, message = "上报间隔不能大于 60 秒")
    private Integer intervalSeconds;

    @NotNull(message = "故障概率不能为空")
    @DecimalMin(value = "0", message = "故障概率不能小于 0")
    @DecimalMax(value = "1", message = "故障概率不能大于 1")
    private BigDecimal faultProbability;
}
