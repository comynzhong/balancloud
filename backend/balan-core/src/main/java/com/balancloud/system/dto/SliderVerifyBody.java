package com.balancloud.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SliderVerifyBody {

    @NotBlank(message = "验证码标识不能为空")
    private String id;

    @NotNull(message = "滑块位置不能为空")
    @Min(value = 0, message = "滑块位置不合法")
    private Integer x;
}
