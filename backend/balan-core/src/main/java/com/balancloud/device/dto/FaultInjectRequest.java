package com.balancloud.device.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FaultInjectRequest {

    @NotBlank(message = "设备编号不能为空")
    private String deviceNo;

    @NotNull(message = "端口号不能为空")
    @Min(value = 1, message = "端口号必须大于 0")
    private Integer portNo;

    @Min(value = 5, message = "故障持续时间不能小于 5 秒")
    @Max(value = 300, message = "故障持续时间不能超过 300 秒")
    private Integer durationSeconds = 30;

    private String faultType = "OVER_TEMP";
}
