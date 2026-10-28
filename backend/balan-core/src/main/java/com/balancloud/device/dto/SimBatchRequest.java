package com.balancloud.device.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SimBatchRequest {

    @NotNull(message = "归属场站不能为空")
    private Long stationId;

    @NotBlank(message = "设备类型不能为空")
    @Pattern(regexp = "SOCKET_2|SOCKET_10|SOCKET_12", message = "设备类型不合法")
    private String deviceType;

    @NotNull(message = "创建数量不能为空")
    @Min(value = 1, message = "至少创建 1 台设备")
    @Max(value = 20, message = "单次最多创建 20 台设备")
    private Integer count;

    @Size(max = 40, message = "名称前缀不能超过 40 个字符")
    private String namePrefix;
}
