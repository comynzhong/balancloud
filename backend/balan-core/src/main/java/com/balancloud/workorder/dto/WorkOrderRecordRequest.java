package com.balancloud.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkOrderRecordRequest {

    @NotBlank(message = "请填写处理记录")
    @Size(max = 500, message = "处理记录不能超过 500 字")
    private String content;
}
