package com.balancloud.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VehicleRequest {

    private Long appUserId;

    @NotBlank(message = "车牌号不能为空")
    @Size(max = 16, message = "车牌号不能超过 16 个字符")
    private String plateNo;

    /** 车辆识别代号，选填；填了必须 17 位且不含 I/O/Q（格式由 Service 统一校验并转大写）。 */
    @Size(max = 17, message = "VIN 必须为 17 位")
    private String vin;

    @Size(max = 32, message = "品牌不能超过 32 个字符")
    private String brand;

    @Size(max = 64, message = "车型不能超过 64 个字符")
    private String model;

    @Size(max = 16, message = "颜色不能超过 16 个字符")
    private String color;

    private Boolean isDefault;
}
