package com.balancloud.rate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RatePeriodRequest {

    @NotBlank(message = "时段类型不能为空")
    private String periodType;

    @NotBlank(message = "时段开始时间不能为空")
    private String startTime;

    @NotBlank(message = "时段结束时间不能为空")
    private String endTime;

    @NotNull(message = "时间单价不能为空")
    @DecimalMin(value = "0", message = "时间单价不能为负数")
    @Digits(integer = 8, fraction = 4, message = "时间单价最多 4 位小数")
    private BigDecimal timePrice;

    @NotNull(message = "电量单价不能为空")
    @DecimalMin(value = "0", message = "电量单价不能为负数")
    @Digits(integer = 8, fraction = 4, message = "电量单价最多 4 位小数")
    private BigDecimal energyPrice;

    @NotNull(message = "服务费单价不能为空")
    @DecimalMin(value = "0", message = "服务费单价不能为负数")
    @Digits(integer = 8, fraction = 4, message = "服务费单价最多 4 位小数")
    private BigDecimal servicePrice;

    @NotBlank(message = "请选择服务费计价单位")
    private String serviceUnit;
}
