package com.balancloud.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SimUnplugRequest {

    @NotBlank(message = "设备编号不能为空")
    private String deviceNo;

    @NotNull(message = "端口号不能为空")
    private Integer portNo;
}
