package com.balancloud.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ManualReasonRequest {

    @NotBlank(message = "人工处理原因不能为空")
    @Size(max = 200, message = "人工处理原因不能超过 200 个字符")
    private String reason;
}
