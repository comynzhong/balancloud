package com.balancloud.device.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StationRequest {

    @NotBlank(message = "场站名称不能为空")
    @Size(max = 100, message = "场站名称不能超过 100 个字符")
    private String name;

    @Size(max = 255, message = "场站地址不能超过 255 个字符")
    private String address;

    @DecimalMin(value = "-90", message = "纬度不合法")
    @DecimalMax(value = "90", message = "纬度不合法")
    @Digits(integer = 3, fraction = 7, message = "纬度最多 7 位小数")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "经度不合法")
    @DecimalMax(value = "180", message = "经度不合法")
    @Digits(integer = 3, fraction = 7, message = "经度最多 7 位小数")
    private BigDecimal longitude;

    @DecimalMin(value = "0", message = "额定容量不能为负数")
    @Digits(integer = 8, fraction = 2, message = "额定容量格式不正确")
    private BigDecimal capacityKw;

    @Size(max = 50, message = "负责人不能超过 50 个字符")
    private String manager;

    @Size(max = 30, message = "联系电话不能超过 30 个字符")
    private String phone;

    @Pattern(regexp = "ACTIVE|DISABLED", message = "场站状态不合法")
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
