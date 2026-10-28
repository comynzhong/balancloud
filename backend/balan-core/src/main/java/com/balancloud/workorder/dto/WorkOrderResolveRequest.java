package com.balancloud.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkOrderResolveRequest {

    @NotBlank(message = "请填写处理结论")
    @Size(max = 500, message = "处理结论不能超过 500 字")
    private String resolution;
}
