package com.balancloud.workorder.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkOrderCloseRequest {

    @Size(max = 200, message = "备注不能超过 200 字")
    private String remark;
}
