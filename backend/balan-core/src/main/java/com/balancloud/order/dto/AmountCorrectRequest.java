package com.balancloud.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AmountCorrectRequest {

    @NotBlank(message = "修正原因不能为空")
    @Size(max = 200, message = "修正原因不能超过 200 个字符")
    private String reason;

    @DecimalMin(value = "0", message = "时间费不能为负数")
    @Digits(integer = 12, fraction = 4, message = "时间费格式不正确")
    private BigDecimal timeFee;

    @DecimalMin(value = "0", message = "电费不能为负数")
    @Digits(integer = 12, fraction = 4, message = "电费格式不正确")
    private BigDecimal energyFee;

    @DecimalMin(value = "0", message = "服务费不能为负数")
    @Digits(integer = 12, fraction = 4, message = "服务费格式不正确")
    private BigDecimal serviceFee;
}
